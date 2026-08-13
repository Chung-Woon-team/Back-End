package com.Chung_Woon.Chung_Woon.domain.plan;

import com.Chung_Woon.Chung_Woon.ai.AiClient;
import com.Chung_Woon.Chung_Woon.ai.dto.ReplanRequest;
import com.Chung_Woon.Chung_Woon.ai.dto.ReplanResponse;
import com.Chung_Woon.Chung_Woon.domain.instruction.InstructionRepository;
import com.Chung_Woon.Chung_Woon.domain.instruction.PlanConstraint;
import com.Chung_Woon.Chung_Woon.domain.instruction.PlanConstraintRepository;
import com.Chung_Woon.Chung_Woon.domain.vehicle.VehicleRepository;
import com.Chung_Woon.Chung_Woon.domain.yard.SlotRepository;
import com.Chung_Woon.Chung_Woon.global.config.AiClientConfig;
import com.Chung_Woon.Chung_Woon.global.error.BusinessException;
import com.Chung_Woon.Chung_Woon.global.error.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * 승인된 제약 + 야드 현황 + 차량 목록을 모아 파이썬 {@code /internal/replan} 을 부르고, 결과를
 * {@link PlanRevision}(+{@link Placement} N + {@link MoveTask} N)으로 저장한다.
 *
 * <p>AI 호출은 트랜잭션 <b>밖</b>이다. 재배치 계산이 수 초 걸릴 수 있는데(AiClientConfig 의 read
 * timeout 은 60초), 트랜잭션 안에서 부르면 그동안 DB 커넥션을 잡고 있는다 — 동시에 몇 건만 겹쳐도
 * 커넥션 풀이 고갈돼 "파이썬이 죽어 있어도 조회 API 는 살아 있어야 한다"(API_CONTRACT.md)를 깬다.
 * 그래서 저장만 {@link TransactionTemplate} 으로 감싼다(BillOfLadingExpansionService 와 같은 패턴).
 */
@Service
@RequiredArgsConstructor
public class ReplanExecutionService {

	private final AiClient aiClient;
	private final YardQueryService yardQueryService;
	private final PlanConstraintRepository planConstraintRepository;
	private final PlanRevisionRepository planRevisionRepository;
	private final VehicleRepository vehicleRepository;
	private final SlotRepository slotRepository;
	private final InstructionRepository instructionRepository;
	private final TransactionTemplate transactionTemplate;

	@Qualifier(AiClientConfig.AI_OBJECT_MAPPER)
	private final ObjectMapper aiObjectMapper;

	public ExecutionResult executeReplan(String basePlanVersion, String triggeredByInstructionId) {
		List<PlanConstraint> approved = planConstraintRepository.findApplicable();

		ReplanResponse response = aiClient.replan(new ReplanRequest(
				basePlanVersion,
				approved.stream().map(this::toConstraintPayload).toList(),
				yardQueryService.currentYardState(),
				yardQueryService.plannableVehicles()
		));

		return transactionTemplate.execute(status -> persist(response, triggeredByInstructionId));
	}

	private ReplanRequest.ConstraintPayload toConstraintPayload(PlanConstraint c) {
		return new ReplanRequest.ConstraintPayload(
				c.getConstraintId(),
				c.getType(),
				readTree(c.getTargetJson()),
				readTree(c.getValueJson()),
				new ReplanRequest.TimeWindow(c.getWindowStart(), c.getWindowEnd()),
				c.getPriority(),
				c.getConfidence(),
				c.getStatus()
		);
	}

	private JsonNode readTree(String json) {
		if (json == null) {
			return null;
		}
		try {
			return aiObjectMapper.readTree(json);
		} catch (JsonProcessingException e) {
			throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID, "저장된 제약 JSON을 읽지 못했습니다.");
		}
	}

	private ExecutionResult persist(ReplanResponse response, String triggeredByInstructionId) {
		PlanRevision revision = PlanRevision.builder()
				.planVersion(response.planVersion())
				.basedOnVersion(response.basedOnVersion())
				.triggeredBy(triggeredByInstructionId != null
						? instructionRepository.getReferenceById(triggeredByInstructionId) : null)
				.kpi(toEmbeddedKpi(response.kpi()))
				.build();

		if (response.placements() != null) {
			response.placements().forEach((vehicleId, slotId) -> revision.getPlacements().add(Placement.builder()
					.planRevision(revision)
					.vehicle(vehicleRepository.getReferenceById(vehicleId))
					.slot(slotRepository.getReferenceById(slotId))
					.build()));
		}

		if (response.moves() != null) {
			response.moves().forEach(m -> revision.getMoveTasks().add(MoveTask.builder()
					.planRevision(revision)
					.vehicle(vehicleRepository.getReferenceById(m.vehicleId()))
					.fromSlot(m.fromSlot() != null ? slotRepository.getReferenceById(m.fromSlot()) : null)
					.toSlot(slotRepository.getReferenceById(m.toSlot()))
					.sequence(m.sequence())
					.reason(m.reason())
					.distanceMeters(m.distanceMeters())
					.build()));
		}

		planRevisionRepository.save(revision);

		return new ExecutionResult(
				revision.getPlanVersion(),
				revision.getBasedOnVersion(),
				revision.getStatus(),
				revision.getPlacements().size(),
				revision.getMoveTasks().size(),
				response.unplaced() != null ? response.unplaced() : List.of(),
				response.moves() == null ? List.of() : response.moves().stream()
						.map(this::toMoveWithPath)
						.toList()
		);
	}

	private MoveWithPath toMoveWithPath(ReplanResponse.Move m) {
		return new MoveWithPath(
				m.vehicleId(), m.fromSlot(), m.toSlot(), m.sequence(), m.reason(),
				m.distanceMeters(), m.path() != null ? m.path() : List.of());
	}

	private PlanKpi toEmbeddedKpi(ReplanResponse.PlanKpi k) {
		if (k == null) {
			return null;
		}
		return PlanKpi.builder()
				.avgMoveDistance(k.avgMoveDistance())
				.rehandleProxy(k.rehandleProxy())
				.hardViolations(k.hardViolations())
				.changedVehicles(k.changedVehicles())
				.planRetentionRate(k.planRetentionRate())
				.calcMillis(k.calcMillis())
				.build();
	}

	/**
	 * {@code unplaced} 와 {@code moves[].path} 는 지금 DB 에 안 남는다(PlanRevision/MoveTask 에
	 * 컬럼 없음, 의도적) — 재배치를 막 계산한 <b>이 응답에서만</b> 볼 수 있다. 나중에
	 * {@code GET /api/plans/{version}} 으로 다시 조회하면 이 둘은 없다.
	 */
	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record ExecutionResult(
			String planVersion,
			String basedOnVersion,
			PlanStatus status,
			int placementCount,
			int moveCount,
			List<String> unplaced,
			List<MoveWithPath> moves
	) {
	}

	/** 프론트 애니메이션용. {@code path} 는 진입도로 ↔ 진입도로 구간만 담는다(mapf.py 참고). */
	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record MoveWithPath(
			String vehicleId,
			String fromSlot,
			String toSlot,
			int sequence,
			String reason,
			Double distanceMeters,
			List<ReplanResponse.PathStep> path
	) {
	}
}
