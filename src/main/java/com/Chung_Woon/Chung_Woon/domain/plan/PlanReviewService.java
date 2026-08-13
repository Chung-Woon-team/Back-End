package com.Chung_Woon.Chung_Woon.domain.plan;

import com.Chung_Woon.Chung_Woon.domain.vehicle.Vehicle;
import com.Chung_Woon.Chung_Woon.domain.yard.Slot;
import com.Chung_Woon.Chung_Woon.global.error.BusinessException;
import com.Chung_Woon.Chung_Woon.global.error.ErrorCode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Revision Log 조회와 승인/반려. {@link PlanConstraint#isApplicable()} 와 같은 안전장치 —
 * {@code APPROVED} 판만 "현재 현장에 나가 있는 계획"으로 취급된다({@link YardQueryService}).
 *
 * <p>엔티티를 컨트롤러로 그대로 내보내지 않고 여기서 DTO 로 바꾼다 — {@code spring.jpa.open-in-view:
 * false} 라 세션이 서비스 메서드 종료와 함께 닫히는데, LAZY 연관관계를 밖에서 건드리면 예외가 난다
 * (InstructionParsingService/ConstraintReviewService 와 같은 이유).
 */
@Service
@RequiredArgsConstructor
public class PlanReviewService {

	private final PlanRevisionRepository planRevisionRepository;
	private final PlacementRepository placementRepository;
	private final MoveTaskRepository moveTaskRepository;

	@Transactional(readOnly = true)
	public List<PlanSummary> list() {
		return planRevisionRepository.findAllByOrderByIdDesc().stream().map(PlanSummary::from).toList();
	}

	@Transactional(readOnly = true)
	public PlanDetail get(String planVersion) {
		PlanRevision revision = getOrThrow(planVersion);

		Map<String, String> placements = placementRepository.findAllByPlanVersion(planVersion).stream()
				.collect(Collectors.toMap(p -> p.getVehicle().getVehicleId(), p -> p.getSlot().getSlotId()));

		List<MoveDto> moves = moveTaskRepository.findByPlanRevision_PlanVersionOrderBySequenceAsc(planVersion)
				.stream().map(MoveDto::from).toList();

		return PlanDetail.from(revision, placements, moves);
	}

	/**
	 * 승인만으로 끝나지 않는다 — 이 판이 "지금 야드의 실제 상태"가 되도록
	 * {@link Vehicle#currentSlot}/{@link Slot#status} 를 같이 갱신한다.
	 *
	 * <p>{@link Placement} 는 판마다 쌓이는 이력이라 그 자체로는 실시간 상태가 아니다. DRAFT 판이
	 * 여러 개 있을 수 있는데 그것들 때문에 실제 상태가 흔들리면 안 되므로, 이 반영은
	 * <b>승인 시점에만</b> 한다(장표 원칙 "승인 전에는 반영 안 됨"과 같은 이유).
	 */
	@Transactional
	public PlanSummary approve(String planVersion, String reviewer) {
		PlanRevision revision = getOrThrow(planVersion);
		revision.approve(reviewer);
		applyToRealYardState(planVersion);
		return PlanSummary.from(revision);
	}

	@Transactional
	public PlanSummary reject(String planVersion, String reviewer) {
		PlanRevision revision = getOrThrow(planVersion);
		revision.reject(reviewer);
		return PlanSummary.from(revision);
	}

	/**
	 * 모든 옛 자리를 먼저 비운 뒤 새 배치를 채운다. 차량 A/B가 자리를 맞바꾸는 계획을 한 대씩
	 * 처리하면, A가 새로 채운 B의 옛 슬롯을 B 처리 시 다시 EMPTY로 만드는 순서 오류가 발생한다.
	 */
	private void applyToRealYardState(String planVersion) {
		List<Placement> placements = placementRepository.findAllByPlanVersion(planVersion);

		for (Placement placement : placements) {
			Vehicle vehicle = placement.getVehicle();
			Slot newSlot = placement.getSlot();
			Slot oldSlot = vehicle.getCurrentSlot();

			if (oldSlot != null && !oldSlot.getSlotId().equals(newSlot.getSlotId())) {
				oldSlot.markEmpty();
			}
		}

		for (Placement placement : placements) {
			Vehicle vehicle = placement.getVehicle();
			Slot newSlot = placement.getSlot();
			vehicle.parkAt(newSlot);
			newSlot.markOccupied();
		}
	}

	private PlanRevision getOrThrow(String planVersion) {
		return planRevisionRepository.findByPlanVersion(planVersion)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "계획을 찾을 수 없습니다: " + planVersion));
	}

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record PlanSummary(
			String planVersion,
			String basedOnVersion,
			PlanStatus status,
			Double avgMoveDistance,
			Integer changedVehicles,
			Integer hardViolations,
			String approvedBy,
			LocalDateTime approvedAt
	) {
		static PlanSummary from(PlanRevision r) {
			PlanKpi k = r.getKpi();
			return new PlanSummary(
					r.getPlanVersion(),
					r.getBasedOnVersion(),
					r.getStatus(),
					k != null ? k.getAvgMoveDistance() : null,
					k != null ? k.getChangedVehicles() : null,
					k != null ? k.getHardViolations() : null,
					r.getApprovedBy(),
					r.getApprovedAt()
			);
		}
	}

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record MoveDto(
			String vehicleId,
			String fromSlot,
			String toSlot,
			int sequence,
			String reason,
			Double distanceMeters
	) {
		static MoveDto from(MoveTask m) {
			return new MoveDto(
					m.getVehicle().getVehicleId(),
					m.getFromSlot() != null ? m.getFromSlot().getSlotId() : null,
					m.getToSlot().getSlotId(),
					m.getSequence(),
					m.getReason(),
					m.getDistanceMeters()
			);
		}
	}

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record PlanDetail(
			String planVersion,
			String basedOnVersion,
			PlanStatus status,
			Map<String, String> placements,
			List<MoveDto> moves,
			Double avgMoveDistance,
			Double rehandleProxy,
			Integer hardViolations,
			Integer changedVehicles,
			Double planRetentionRate,
			Long calcMillis
	) {
		static PlanDetail from(PlanRevision r, Map<String, String> placements, List<MoveDto> moves) {
			PlanKpi k = r.getKpi();
			return new PlanDetail(
					r.getPlanVersion(),
					r.getBasedOnVersion(),
					r.getStatus(),
					placements,
					moves,
					k != null ? k.getAvgMoveDistance() : null,
					k != null ? k.getRehandleProxy() : null,
					k != null ? k.getHardViolations() : null,
					k != null ? k.getChangedVehicles() : null,
					k != null ? k.getPlanRetentionRate() : null,
					k != null ? k.getCalcMillis() : null
			);
		}
	}
}
