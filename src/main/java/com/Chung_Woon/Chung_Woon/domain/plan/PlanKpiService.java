package com.Chung_Woon.Chung_Woon.domain.plan;

import com.Chung_Woon.Chung_Woon.api.dto.PlanKpiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 판 하나의 KPI 를 이전 판과 대조해 화면이 그대로 그릴 수 있는 형태로 만든다.
 *
 * <p><b>수치 6개를 다시 계산하지 않는다.</b> 재배치 계산 시점에 파이썬이 계산해
 * {@code plan_revision} 에 저장한 값을 읽기만 하고, 여기서 새로 만드는 건 표시용 파생값
 * (index / delta / label / tone) 뿐이다. 같은 수치를 두 곳에서 계산하면 반드시 어긋난다.
 *
 * <p><b>파이썬을 부르지 않는다.</b> AI 서비스가 죽어 있어도 이 API 는 200 이어야 한다
 * (API_CONTRACT.md "파이썬이 죽어 있을 때").
 *
 * <p>{@code before} 는 별도 컬럼이 아니라 {@code basedOnVersion} 으로 이전 판을 조회해 얻는다 —
 * {@link com.Chung_Woon.Chung_Woon.domain.dashboard.DashboardService} 가 이미 쓰는 검증된 패턴이고
 * 스키마 변경이 없다.
 */
@Service
@RequiredArgsConstructor
public class PlanKpiService {

	// 거리 축 단위. 파이썬이 meters_per_cell 을 이미 곱해 미터로 내려준다.
	private static final String DISTANCE_UNIT = "METER";
	private static final String DISTANCE_UNIT_LABEL = "m";

	private static final String LOWER = "LOWER";
	private static final String HIGHER = "HIGHER";

	private static final String GOOD = "GOOD";
	private static final String WARN = "WARN";
	private static final String NEUTRAL = "NEUTRAL";

	/** 장표 목표: 500대 2초 이내. 넘으면 경고색으로 그린다. */
	private static final long CALC_MILLIS_WARN_THRESHOLD = 2000L;

	private final PlanRevisionRepository planRevisionRepository;
	private final PlanVersionResolver planVersionResolver;

	@Transactional(readOnly = true)
	public PlanKpiResponse load(String planVersion) {
		PlanRevision revision = planVersionResolver.resolve(planVersion);

		PlanKpi after = revision.getKpi();
		PlanKpi before = previousKpi(revision);
		boolean hasKpi = after != null;
		boolean comparable = hasKpi && before != null;

		return new PlanKpiResponse(
				revision.getPlanVersion(),
				revision.getBasedOnVersion(),
				revision.getStatus(),
				statusLabel(revision.getStatus()),
				hasKpi,
				comparable,
				DISTANCE_UNIT,
				DISTANCE_UNIT_LABEL,
				hasKpi ? metrics(after, before) : List.of(),
				hasKpi ? highlights(after) : List.of()
		);
	}

	/**
	 * 비교 대상이 되는 이전 판의 KPI. 이전 판이 없거나 그 판에 KPI 가 없으면 null 이고,
	 * 그러면 전후 비교 자체를 표시하지 않는다 — 없는 숫자를 지어내는 것보다 낫다.
	 */
	private PlanKpi previousKpi(PlanRevision revision) {
		if (revision.getBasedOnVersion() == null) {
			return null;
		}
		return planRevisionRepository.findByPlanVersion(revision.getBasedOnVersion())
				.map(PlanRevision::getKpi)
				.orElse(null);
	}

	// ------------------------------------------------------------------ metrics

	/** 배열 순서 고정. 프론트가 정렬하지 않는다. */
	private List<PlanKpiResponse.Metric> metrics(PlanKpi after, PlanKpi before) {
		List<PlanKpiResponse.Metric> metrics = new ArrayList<>();
		addIfPresent(metrics, metric("avg_move_distance", "평균 이동거리", DISTANCE_UNIT, DISTANCE_UNIT_LABEL,
				LOWER, after.getAvgMoveDistance(), before != null ? before.getAvgMoveDistance() : null));
		addIfPresent(metrics, metric("rehandle_proxy", "재취급 Proxy", "DEPTH", "depth",
				LOWER, after.getRehandleProxy(), before != null ? before.getRehandleProxy() : null));
		addIfPresent(metrics, metric("plan_retention_rate", "계획 유지율", "PERCENT", "%",
				HIGHER, after.getPlanRetentionRate(), before != null ? before.getPlanRetentionRate() : null));
		return metrics;
	}

	private static void addIfPresent(List<PlanKpiResponse.Metric> metrics, PlanKpiResponse.Metric metric) {
		if (metric != null) {
			metrics.add(metric);
		}
	}

	/**
	 * 지표 하나. {@code after} 가 없으면 <b>원소 자체를 빼버린다</b> — 값만 비운 원소를 남기면
	 * 프론트가 빈 막대를 그리게 된다.
	 *
	 * <p>{@code before == 0.0} 이면 0 나눗셈을 피해 그 지표만 비교 불가로 처리한다.
	 */
	private PlanKpiResponse.Metric metric(String key, String label, String unit, String unitLabel,
			String better, Double after, Double before) {
		if (after == null) {
			return null;
		}
		if (before == null || before == 0.0) {
			return new PlanKpiResponse.Metric(key, label, unit, unitLabel,
					null, after, null, null, null, null, null, better, NEUTRAL);
		}

		// 반올림하지 않은 원값으로 계산하고 마지막에 한 번만 반올림한다. 표시용으로 반올림된
		// "1.9 → 1.2" 와 "36% 감소" 를 손으로 맞춰보면 안 맞아 보이는데(원값 1.93 → 1.24),
		// 화면 숫자에 맞춰 반올림 후 계산하면 증감률이 실제와 달라진다.
		double indexAfter = round1(after / before * 100.0);
		double deltaAbs = round1(after - before);
		double deltaPct = round1((after - before) / before * 100.0);

		return new PlanKpiResponse.Metric(key, label, unit, unitLabel,
				before, after, 100.0, indexAfter, deltaAbs, deltaPct,
				deltaLabel(deltaPct), better, tone(better, deltaPct));
	}

	/** 세 지표 모두 퍼센트 하나로 통일한다 — 지표별 특례를 만들면 파이썬 구현과 반드시 어긋난다. */
	static String deltaLabel(double deltaPct) {
		long p = Math.round(Math.abs(deltaPct));
		if (deltaPct < 0) {
			return p + "% 감소";
		}
		if (deltaPct > 0) {
			return p + "% 증가";
		}
		return "변화 없음";
	}

	private static String tone(String better, double deltaPct) {
		boolean improved = (LOWER.equals(better) && deltaPct < 0) || (HIGHER.equals(better) && deltaPct > 0);
		if (improved) {
			return GOOD;
		}
		boolean worsened = (LOWER.equals(better) && deltaPct > 0) || (HIGHER.equals(better) && deltaPct < 0);
		return worsened ? WARN : NEUTRAL;
	}

	// --------------------------------------------------------------- highlights

	private List<PlanKpiResponse.Highlight> highlights(PlanKpi kpi) {
		List<PlanKpiResponse.Highlight> highlights = new ArrayList<>();
		if (kpi.getHardViolations() != null) {
			highlights.add(new PlanKpiResponse.Highlight("hard_violations", "Hard 제약 위반",
					kpi.getHardViolations(), "COUNT", "건",
					kpi.getHardViolations() == 0 ? GOOD : WARN));
		}
		if (kpi.getChangedVehicles() != null) {
			// 많이 움직였다고 나쁜 판이 아니다(블록 하나가 통째로 닫히면 당연히 많다). 색을 칠하지 않는다.
			highlights.add(new PlanKpiResponse.Highlight("changed_vehicles", "이동 대상 차량",
					kpi.getChangedVehicles(), "VEHICLE", "대", NEUTRAL));
		}
		if (kpi.getCalcMillis() != null) {
			highlights.add(new PlanKpiResponse.Highlight("calc_millis", "계산 소요",
					kpi.getCalcMillis(), "MILLIS", "ms",
					kpi.getCalcMillis() <= CALC_MILLIS_WARN_THRESHOLD ? GOOD : WARN));
		}
		return highlights;
	}

	// ------------------------------------------------------------------ 공통

	static String statusLabel(PlanStatus status) {
		return switch (status) {
			case DRAFT -> "검토 전";
			case PENDING_APPROVAL -> "승인 대기";
			case APPROVED -> "승인됨";
			case REJECTED -> "반려됨";
		};
	}

	/**
	 * 소수 첫째 자리까지. 파이썬 쪽도 같은 규칙으로 구현한다 — 언어 기본 {@code round()} 는
	 * 은행가 반올림(0.5 를 짝수로)이라 그대로 쓰면 두 구현의 문구가 갈린다.
	 */
	static double round1(double value) {
		return Math.round(value * 10.0) / 10.0;
	}
}
