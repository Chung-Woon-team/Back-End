package com.Chung_Woon.Chung_Woon.domain.plan;

import com.Chung_Woon.Chung_Woon.ai.AiClient;
import com.Chung_Woon.Chung_Woon.ai.dto.BriefRequest;
import com.Chung_Woon.Chung_Woon.ai.dto.BriefResponse;
import com.Chung_Woon.Chung_Woon.api.dto.PlanBriefingResponse;
import com.Chung_Woon.Chung_Woon.global.config.AiClientConfig;
import com.Chung_Woon.Chung_Woon.global.error.BusinessException;
import com.Chung_Woon.Chung_Woon.global.error.ErrorCode;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 브리핑 조회/생성.
 *
 * <p><b>읽기와 쓰기를 갈라 놓았다.</b> {@link #load}(GET)는 DB 만 읽어서 파이썬이 죽어 있어도 200 이고,
 * {@link #generate}(POST)만 파이썬을 탄다(API_CONTRACT.md "파이썬이 죽어 있어도 조회 API 는 살아
 * 있어야 한다").
 *
 * <p>AI 호출은 트랜잭션 <b>밖</b>이다 — read timeout 이 60초라 트랜잭션 안에서 부르면 그동안 DB
 * 커넥션을 잡고 있어 풀이 고갈된다({@link ReplanExecutionService} 와 같은 이유).
 *
 * <p><b>브리핑 문장을 자바가 조립하지 않는다.</b> 파이썬이 죽었을 때 자바가 대신 문장을 만들면 같은
 * 판에 대해 서로 다른 문장이 두 벌 생긴다. 폴백 문장은 전적으로 파이썬 책임이다(파이썬은 Gemini 가
 * 없어도 수치 템플릿으로 200 을 준다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanBriefingService {

	/** 이 판 평균의 몇 배를 넘으면 "먼 이동" 으로 볼지. */
	private static final double LONG_MOVE_FACTOR = 2.0;

	/** LONG_MOVE 경고는 상위 몇 건까지만. 100건씩 뜨면 아무도 안 읽는다. */
	private static final int LONG_MOVE_LIMIT = 3;

	/** 유지율이 이 밑이면 부분 재배치 범위가 과도한 것으로 본다. */
	private static final double RETENTION_WARN_THRESHOLD = 80.0;

	/**
	 * 파이썬에 실어 보낼 이동 최대 건수. 500대 판에서 요청이 비대해지는 걸 막는다 — 파이썬은
	 * 이 배열의 개수를 세지 않고 대수는 {@code kpi.changed_vehicles} 를 정본으로 쓴다.
	 */
	private static final int MOVE_PAYLOAD_LIMIT = 200;

	private final PlanRevisionRepository planRevisionRepository;
	private final MoveTaskRepository moveTaskRepository;
	private final PlanVersionResolver planVersionResolver;
	private final AiClient aiClient;
	private final TransactionTemplate transactionTemplate;

	@Qualifier(AiClientConfig.AI_OBJECT_MAPPER)
	private final ObjectMapper aiObjectMapper;

	// ------------------------------------------------------------------ 조회

	/**
	 * 저장된 브리핑을 그대로 읽는다. 아직 생성 전이어도 404 가 아니라 200 이다 —
	 * 프론트는 {@code state} 로 "브리핑 생성" 버튼을 띄울지 결정한다.
	 */
	@Transactional(readOnly = true)
	public PlanBriefingResponse load(String planVersion) {
		PlanRevision revision = planVersionResolver.resolve(planVersion);
		return toResponse(revision.getPlanVersion(), revision.getBriefing(),
				parseMeta(revision.getConsistencyIssuesJson()));
	}

	// ------------------------------------------------------------------ 생성

	/**
	 * 브리핑을 만들어 저장하고 {@link #load} 와 <b>완전히 같은 모양</b>으로 돌려준다.
	 * 호출할 때마다 다시 만들어 덮어쓴다(멱등하지 않음) — 프론트의 "다시 생성" 버튼이 같은 요청을 쓴다.
	 */
	public PlanBriefingResponse generate(String planVersion) {
		// 1~5단계: 페이로드 조립. MoveTask 의 vehicle/toSlot 이 LAZY 라 세션 안에서 읽어야 한다
		// (open-in-view: false). 그래서 AI 호출 전 준비 구간을 트랜잭션으로 감싼다.
		Payload payload = transactionTemplate.execute(status -> preparePayload(planVersion));

		// 6단계: AI 호출 — 트랜잭션 밖.
		BriefResponse response = aiClient.brief(payload.request());

		// 7단계: 응답 검증.
		if (response == null || response.briefing() == null || response.briefing().isBlank()) {
			throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID, "브리핑 문장을 받지 못했습니다.");
		}
		if (response.confirmations() != null && !response.confirmations().isEmpty()) {
			// 파이썬이 돌려준 목록은 자바가 보낸 것의 에코라 쓰지 않는다. 개수가 다르면 계약 위반이므로
			// 눈에 띄게 남긴다(공개 응답은 흔들리지 않는다 — 자바가 만든 목록이 유일한 정본).
			int echoed = response.confirmations().size();
			if (echoed != payload.confirmations().size()) {
				log.warn("파이썬 confirmations 에코가 요청과 다르다 (보냄 {} / 받음 {}). 자바 목록을 쓴다.",
						payload.confirmations().size(), echoed);
			}
		}

		String source = normalizeSource(response.source());
		LocalDateTime generatedAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
		String metaJson = writeMeta(new BriefingMeta(generatedAt, source, payload.confirmations()));

		// 8단계: 저장만 트랜잭션 안에서. resolve 는 이미 끝났으므로 해석된 번호로 다시 잡는다.
		transactionTemplate.execute(status -> {
			PlanRevision managed = planRevisionRepository.findByPlanVersion(payload.planVersion())
					.orElseThrow(() -> new BusinessException(
							ErrorCode.NOT_FOUND, "계획을 찾을 수 없습니다: " + payload.planVersion()));
			managed.attachBriefing(response.briefing());
			managed.attachBriefingMeta(metaJson);
			return null;
		});

		return toResponse(payload.planVersion(), response.briefing(),
				new BriefingMeta(generatedAt, source, payload.confirmations()));
	}

	/** 1~5단계. 트랜잭션 안에서 돈다. */
	private Payload preparePayload(String planVersion) {
		PlanRevision revision = planVersionResolver.resolve(planVersion);

		PlanKpi kpi = revision.getKpi();
		if (!isComplete(kpi)) {
			// 파이썬 PlanKpi 는 extra="forbid" + 6필드 전부 필수라 null 을 보내면 422 로 튕긴다.
			// 그 전에 여기서 막아야 사용자가 읽을 수 있는 에러가 나간다.
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"KPI 가 없는 판은 브리핑을 만들 수 없습니다: " + revision.getPlanVersion());
		}

		PlanKpi beforeKpi = previousKpi(revision);
		// 부분적으로 채워진 kpi_before 는 보내지 않는다 — 같은 422 이유.
		BriefRequest.Kpi kpiBefore = isComplete(beforeKpi) ? toPayloadKpi(beforeKpi) : null;

		List<MoveTask> moves =
				moveTaskRepository.findByPlanRevision_PlanVersionOrderBySequenceAsc(revision.getPlanVersion());

		List<PlanBriefingResponse.Confirmation> confirmations =
				buildConfirmations(revision, kpi, beforeKpi, moves);

		BriefRequest request = new BriefRequest(
				toPayloadKpi(kpi),
				kpiBefore,
				moves.stream().limit(MOVE_PAYLOAD_LIMIT).map(PlanBriefingService::toPayloadMove).toList(),
				confirmations.stream().map(PlanBriefingService::toPayloadIssue).toList()
		);

		return new Payload(revision.getPlanVersion(), request, confirmations);
	}

	// --------------------------------------------------------- confirmations

	/**
	 * "확인이 필요한 지점" 을 결정론적으로 만든다. <b>자바가 유일한 정본</b>이고 순서도 고정이다 —
	 * 이 목록을 (a) 파이썬에 보내고 (b) 판에 저장하고 (c) 응답으로 내려주는 게 전부 같은 배열이라,
	 * 파이썬 구현과 어긋나도 공개 응답이 흔들리지 않는다.
	 */
	private List<PlanBriefingResponse.Confirmation> buildConfirmations(
			PlanRevision revision, PlanKpi kpi, PlanKpi beforeKpi, List<MoveTask> moves) {

		List<PlanBriefingResponse.Confirmation> confirmations = new ArrayList<>();

		if (kpi.getHardViolations() != null && kpi.getHardViolations() > 0) {
			confirmations.add(new PlanBriefingResponse.Confirmation(
					"HARD_VIOLATION", "WARN",
					"Hard 제약 위반 " + kpi.getHardViolations() + "건이 남아 있습니다.",
					"미배치 차량을 수동으로 배정한 뒤 승인하세요.", null));
		}

		if (kpi.getPlanRetentionRate() != null && kpi.getPlanRetentionRate() < RETENTION_WARN_THRESHOLD) {
			confirmations.add(new PlanBriefingResponse.Confirmation(
					"RETENTION_DROP", "WARN",
					"계획 유지율이 " + fmt(kpi.getPlanRetentionRate())
							+ "% 로 낮습니다. 이전 판 대비 자리를 지킨 차량이 적습니다.",
					"부분 재배치 범위가 과도하지 않은지 확인하세요.", null));
		}

		Double avg = kpi.getAvgMoveDistance();
		if (avg != null && avg > 0) {
			moves.stream()
					.filter(m -> m.getDistanceMeters() != null && m.getDistanceMeters() > avg * LONG_MOVE_FACTOR)
					.sorted(Comparator.comparingDouble(MoveTask::getDistanceMeters).reversed())
					.limit(LONG_MOVE_LIMIT)
					.forEach(m -> confirmations.add(new PlanBriefingResponse.Confirmation(
							"LONG_MOVE", "WARN",
							m.getVehicle().getVehicleId() + " 의 이동거리가 " + fmt(m.getDistanceMeters())
									+ "m 로 이 판 평균(" + fmt(avg) + "m)의 2배를 넘습니다.",
							"더 가까운 대체 슬롯이 있는지 확인하세요.",
							m.getToSlot().getSlotId())));
		}

		if (beforeKpi == null) {
			confirmations.add(new PlanBriefingResponse.Confirmation(
					"NO_BASELINE", "INFO",
					"비교할 이전 판이 없어 전후 비교를 표시하지 않습니다.", null, null));
		}
		// OBSERVATION_MISMATCH 는 사진↔계획 대조 결과(SlotDiff)를 판에 연결하는 저장 경로가 아직 없어
		// v1 에서는 만들지 않는다. 코드값만 스웨거에 예약해 뒀다 — 없는 데이터를 지어내지 않는다.

		return confirmations;
	}

	// ------------------------------------------------------------------ 변환

	private PlanKpi previousKpi(PlanRevision revision) {
		if (revision.getBasedOnVersion() == null) {
			return null;
		}
		return planRevisionRepository.findByPlanVersion(revision.getBasedOnVersion())
				.map(PlanRevision::getKpi)
				.orElse(null);
	}

	private static boolean isComplete(PlanKpi kpi) {
		return kpi != null
				&& kpi.getAvgMoveDistance() != null
				&& kpi.getRehandleProxy() != null
				&& kpi.getHardViolations() != null
				&& kpi.getChangedVehicles() != null
				&& kpi.getPlanRetentionRate() != null
				&& kpi.getCalcMillis() != null;
	}

	private static BriefRequest.Kpi toPayloadKpi(PlanKpi k) {
		return new BriefRequest.Kpi(k.getAvgMoveDistance(), k.getRehandleProxy(), k.getHardViolations(),
				k.getChangedVehicles(), k.getPlanRetentionRate(), k.getCalcMillis());
	}

	private static BriefRequest.Move toPayloadMove(MoveTask m) {
		return new BriefRequest.Move(
				m.getVehicle().getVehicleId(),
				m.getFromSlot() != null ? m.getFromSlot().getSlotId() : null,
				m.getToSlot().getSlotId(),
				m.getSequence(),
				m.getReason(),
				m.getDistanceMeters());
	}

	private static BriefRequest.ConsistencyIssue toPayloadIssue(PlanBriefingResponse.Confirmation c) {
		return new BriefRequest.ConsistencyIssue(
				c.code(), c.severity(), c.message(), c.actionHint(), c.slotId());
	}

	/** 파이썬이 이상한 값을 주거나 필드를 빠뜨려도 화면 라벨이 비지 않게 한다. */
	private static String normalizeSource(String source) {
		return "FALLBACK".equals(source) ? "FALLBACK" : "AI";
	}

	private static String sourceLabel(String source) {
		return "FALLBACK".equals(source) ? "수치 기반 자동 작성" : "AI 생성";
	}

	private PlanBriefingResponse toResponse(String planVersion, String briefing, BriefingMeta meta) {
		boolean generated = briefing != null && !briefing.isBlank();
		if (!generated) {
			return new PlanBriefingResponse(planVersion, "NOT_GENERATED", "생성 전",
					null, null, null, null, List.of());
		}
		String source = meta != null ? meta.source() : null;
		List<PlanBriefingResponse.Confirmation> confirmations =
				meta != null && meta.confirmations() != null ? meta.confirmations() : List.of();
		return new PlanBriefingResponse(planVersion, "GENERATED", "생성됨",
				source,
				source != null ? sourceLabel(source) : null,
				meta != null ? meta.generatedAt() : null,
				briefing,
				confirmations);
	}

	// ------------------------------------------------------- 메타 스냅샷 JSON

	private String writeMeta(BriefingMeta meta) {
		try {
			return aiObjectMapper.writeValueAsString(meta);
		} catch (JsonProcessingException e) {
			throw new BusinessException(ErrorCode.INTERNAL_ERROR, "브리핑 메타를 저장하지 못했습니다.");
		}
	}

	/**
	 * 파싱에 실패하면(수기 수정 등) 500 을 내지 않고 confirmations 없이 내려준다 — 브리핑 문장 자체는
	 * 멀쩡한데 메타 한 줄 때문에 화면이 통째로 안 뜨는 게 더 나쁘다.
	 */
	private BriefingMeta parseMeta(String json) {
		if (json == null || json.isBlank()) {
			return null;
		}
		try {
			return aiObjectMapper.readValue(json, BriefingMeta.class);
		} catch (JsonProcessingException e) {
			log.warn("브리핑 메타 JSON 을 읽지 못했습니다: {}", e.getMessage());
			return null;
		}
	}

	/**
	 * {@code plan_revision.consistency_issues_json} 에 저장하는 스냅샷. 내부 전용이라 이 모양 그대로
	 * 외부로 나가지는 않는다.
	 */
	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	record BriefingMeta(
			@JsonProperty("generated_at") LocalDateTime generatedAt,
			@JsonProperty("source") String source,
			@JsonProperty("confirmations") List<PlanBriefingResponse.Confirmation> confirmations
	) {
	}

	/** 준비 구간(트랜잭션 안)에서 만들어 AI 호출 구간(트랜잭션 밖)으로 넘기는 값 묶음. */
	private record Payload(
			String planVersion,
			BriefRequest request,
			List<PlanBriefingResponse.Confirmation> confirmations
	) {
	}

	/**
	 * {@code round1} 후 소수부가 0 이면 정수로 적는다 — "812.0m" 이 아니라 "812m" 으로 읽혀야 한다.
	 * 파이썬 브리핑 템플릿의 {@code fmt()} 와 같은 규칙이다.
	 */
	static String fmt(double value) {
		double rounded = PlanKpiService.round1(value);
		if (rounded == Math.rint(rounded)) {
			return String.valueOf((long) rounded);
		}
		return String.valueOf(rounded);
	}
}
