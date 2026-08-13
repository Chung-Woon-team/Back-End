package com.Chung_Woon.Chung_Woon.domain.plan;

import com.Chung_Woon.Chung_Woon.api.dto.PlanKpiResponse;
import com.Chung_Woon.Chung_Woon.global.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * KPI 파생값 계산만 검증한다 — 스프링 컨텍스트 없이 Mockito mock 리포지토리 + 생성자 직접 조립
 * (PlanReviewServiceTest 와 같은 패턴).
 *
 * <p>여기서 값까지 못 박아 두는 이유: 같은 계산식을 파이썬 브리핑 템플릿도 구현하는데,
 * 반올림 한 자리가 어긋나면 화면의 "38% 감소" 와 브리핑 문장의 "37% 감소" 가 갈린다.
 */
class PlanKpiServiceTest {

	private final PlanRevisionRepository planRevisionRepository = mock(PlanRevisionRepository.class);

	private PlanKpiService service;

	@BeforeEach
	void setUp() {
		service = new PlanKpiService(planRevisionRepository, new PlanVersionResolver(planRevisionRepository));
	}

	private static PlanKpi kpi(Double avg, Double rehandle, Integer hard, Integer changed,
			Double retention, Long millis) {
		return PlanKpi.builder()
				.avgMoveDistance(avg).rehandleProxy(rehandle).hardViolations(hard)
				.changedVehicles(changed).planRetentionRate(retention).calcMillis(millis)
				.build();
	}

	/** 계약 본문의 대표 예시 그대로 — 812 → 502 / 1.93 → 1.24 / 86.9 → 91.6. */
	private void givenExampleChain() {
		PlanRevision before = PlanRevision.builder()
				.planVersion("B0-r1c2d34").status(PlanStatus.APPROVED)
				.kpi(kpi(812.0, 1.93, 2, 61, 86.9, 2110L)).build();
		PlanRevision after = PlanRevision.builder()
				.planVersion("B0-r7f3a91").basedOnVersion("B0-r1c2d34").status(PlanStatus.APPROVED)
				.kpi(kpi(502.0, 1.24, 0, 42, 91.6, 1740L)).build();
		when(planRevisionRepository.findByPlanVersion("B0-r1c2d34")).thenReturn(Optional.of(before));
		when(planRevisionRepository.findByPlanVersion("B0-r7f3a91")).thenReturn(Optional.of(after));
	}

	private static PlanKpiResponse.Metric metric(PlanKpiResponse response, String key) {
		return response.metrics().stream().filter(m -> m.key().equals(key)).findFirst().orElseThrow();
	}

	private static PlanKpiResponse.Highlight highlight(PlanKpiResponse response, String key) {
		return response.highlights().stream().filter(h -> h.key().equals(key)).findFirst().orElseThrow();
	}

	@Test
	@DisplayName("이전 판이 있으면 지수·증감률·증감문구를 계약의 예시값 그대로 만든다")
	void computesDerivedValuesAgainstThePreviousRevision() {
		givenExampleChain();

		PlanKpiResponse response = service.load("B0-r7f3a91");

		assertThat(response.planVersion()).isEqualTo("B0-r7f3a91");
		assertThat(response.basedOnVersion()).isEqualTo("B0-r1c2d34");
		assertThat(response.hasKpi()).isTrue();
		assertThat(response.comparable()).isTrue();
		assertThat(response.unit()).isEqualTo("METER");
		assertThat(response.statusLabel()).isEqualTo("승인됨");

		PlanKpiResponse.Metric distance = metric(response, "avg_move_distance");
		assertThat(distance.before()).isEqualTo(812.0);
		assertThat(distance.after()).isEqualTo(502.0);
		assertThat(distance.indexBefore()).isEqualTo(100.0);
		assertThat(distance.indexAfter()).isEqualTo(61.8);
		assertThat(distance.deltaAbs()).isEqualTo(-310.0);
		assertThat(distance.deltaPct()).isEqualTo(-38.2);
		assertThat(distance.deltaLabel()).isEqualTo("38% 감소");
		assertThat(distance.better()).isEqualTo("LOWER");
		assertThat(distance.tone()).isEqualTo("GOOD");

		// 표시용으로 반올림된 1.9 → 1.2 와 "36% 감소" 는 손으로 맞춰보면 안 맞는데, 원값
		// 1.93 → 1.24 로 계산한 결과라 이게 맞다.
		PlanKpiResponse.Metric rehandle = metric(response, "rehandle_proxy");
		assertThat(rehandle.indexAfter()).isEqualTo(64.2);
		assertThat(rehandle.deltaPct()).isEqualTo(-35.8);
		assertThat(rehandle.deltaLabel()).isEqualTo("36% 감소");
		assertThat(rehandle.unitLabel()).isEqualTo("depth");

		PlanKpiResponse.Metric retention = metric(response, "plan_retention_rate");
		assertThat(retention.indexAfter()).isEqualTo(105.4);
		assertThat(retention.deltaAbs()).isEqualTo(4.7);
		assertThat(retention.deltaPct()).isEqualTo(5.4);
		assertThat(retention.deltaLabel()).isEqualTo("5% 증가");
		assertThat(retention.better()).isEqualTo("HIGHER");
		// 올라간 게 좋은 지표라 증가가 GOOD 이다.
		assertThat(retention.tone()).isEqualTo("GOOD");
	}

	@Test
	@DisplayName("metrics 배열 순서는 고정이다 — 프론트가 정렬하지 않는다")
	void metricOrderIsFixed() {
		givenExampleChain();

		assertThat(service.load("B0-r7f3a91").metrics())
				.extracting(PlanKpiResponse.Metric::key)
				.containsExactly("avg_move_distance", "rehandle_proxy", "plan_retention_rate");
	}

	@Test
	@DisplayName("highlights 의 tone 은 값으로만 정해진다")
	void highlightTonesAreDeterministic() {
		givenExampleChain();
		PlanKpiResponse good = service.load("B0-r7f3a91");

		assertThat(highlight(good, "hard_violations").tone()).isEqualTo("GOOD");
		assertThat(highlight(good, "changed_vehicles").tone()).isEqualTo("NEUTRAL");
		assertThat(highlight(good, "calc_millis").tone()).isEqualTo("GOOD");

		// 위반이 남아 있고 2초를 넘긴 판(= 이전 판)은 둘 다 WARN 이다.
		PlanKpiResponse warn = service.load("B0-r1c2d34");
		assertThat(highlight(warn, "hard_violations").tone()).isEqualTo("WARN");
		assertThat(highlight(warn, "calc_millis").tone()).isEqualTo("WARN");
		assertThat(highlight(warn, "changed_vehicles").tone()).isEqualTo("NEUTRAL");
	}

	@Test
	@DisplayName("정확히 2000ms 는 아직 GOOD 이다 (경계)")
	void calcMillisBoundaryIsInclusive() {
		PlanRevision revision = PlanRevision.builder().planVersion("B0-r0").status(PlanStatus.DRAFT)
				.kpi(kpi(0.0, 0.0, 0, 0, 100.0, 2000L)).build();
		when(planRevisionRepository.findByPlanVersion("B0-r0")).thenReturn(Optional.of(revision));

		assertThat(highlight(service.load("B0-r0"), "calc_millis").tone()).isEqualTo("GOOD");
	}

	@Test
	@DisplayName("비교할 이전 판이 없으면 before·index·delta 가 전부 null 이라 키에서 빠진다")
	void baselineRevisionHasNoComparisonFields() {
		PlanRevision baseline = PlanRevision.builder()
				.planVersion("B0-r1c2d34").status(PlanStatus.APPROVED)
				.kpi(kpi(812.0, 1.93, 2, 61, 86.9, 2110L)).build();
		when(planRevisionRepository.findByPlanVersion("B0-r1c2d34")).thenReturn(Optional.of(baseline));

		PlanKpiResponse response = service.load("B0-r1c2d34");

		assertThat(response.comparable()).isFalse();
		assertThat(response.basedOnVersion()).isNull();
		assertThat(response.metrics()).hasSize(3).allSatisfy(m -> {
			assertThat(m.before()).isNull();
			assertThat(m.indexBefore()).isNull();
			assertThat(m.indexAfter()).isNull();
			assertThat(m.deltaAbs()).isNull();
			assertThat(m.deltaPct()).isNull();
			assertThat(m.deltaLabel()).isNull();
			assertThat(m.after()).isNotNull();
			assertThat(m.tone()).isEqualTo("NEUTRAL");
		});
		// 값이 있는 highlights 는 그대로 나온다 — 비교 불가와 무관하다.
		assertThat(response.highlights()).hasSize(3);
	}

	@Test
	@DisplayName("이전 판 번호는 있는데 그 판을 못 찾으면 비교 불가로 떨어진다")
	void missingPreviousRevisionFallsBackToNonComparable() {
		PlanRevision revision = PlanRevision.builder()
				.planVersion("B0-r2").basedOnVersion("B0-r1").status(PlanStatus.DRAFT)
				.kpi(kpi(502.0, 1.24, 0, 42, 91.6, 1740L)).build();
		when(planRevisionRepository.findByPlanVersion("B0-r2")).thenReturn(Optional.of(revision));
		when(planRevisionRepository.findByPlanVersion("B0-r1")).thenReturn(Optional.empty());

		PlanKpiResponse response = service.load("B0-r2");

		assertThat(response.comparable()).isFalse();
		assertThat(metric(response, "avg_move_distance").before()).isNull();
	}

	@Test
	@DisplayName("이전 값이 0.0 이면 그 지표만 비교 불가다 (0 나눗셈 회피)")
	void zeroBeforeDisablesOnlyThatMetric() {
		PlanRevision before = PlanRevision.builder().planVersion("B0-r1").status(PlanStatus.APPROVED)
				.kpi(kpi(0.0, 1.93, 0, 0, 86.9, 500L)).build();
		PlanRevision after = PlanRevision.builder().planVersion("B0-r2").basedOnVersion("B0-r1")
				.status(PlanStatus.APPROVED).kpi(kpi(502.0, 1.24, 0, 42, 91.6, 1740L)).build();
		when(planRevisionRepository.findByPlanVersion("B0-r1")).thenReturn(Optional.of(before));
		when(planRevisionRepository.findByPlanVersion("B0-r2")).thenReturn(Optional.of(after));

		PlanKpiResponse response = service.load("B0-r2");

		// 최상위는 비교 가능 상태를 유지한다 — 나머지 두 지표는 멀쩡히 비교된다.
		assertThat(response.comparable()).isTrue();
		assertThat(metric(response, "avg_move_distance").before()).isNull();
		assertThat(metric(response, "avg_move_distance").tone()).isEqualTo("NEUTRAL");
		assertThat(metric(response, "rehandle_proxy").before()).isEqualTo(1.93);
	}

	@Test
	@DisplayName("KPI 자체가 없는 판은 404 가 아니라 빈 배열로 200 이다")
	void revisionWithoutKpiReturnsEmptyArrays() {
		PlanRevision revision = PlanRevision.builder()
				.planVersion("B0-r0a0b0c").status(PlanStatus.DRAFT).build();
		when(planRevisionRepository.findByPlanVersion("B0-r0a0b0c")).thenReturn(Optional.of(revision));

		PlanKpiResponse response = service.load("B0-r0a0b0c");

		assertThat(response.hasKpi()).isFalse();
		assertThat(response.comparable()).isFalse();
		assertThat(response.metrics()).isEmpty();
		assertThat(response.highlights()).isEmpty();
		assertThat(response.statusLabel()).isEqualTo("검토 전");
		// 단위는 KPI 유무와 무관하게 항상 내려간다 — 프론트가 축 라벨을 그려야 한다.
		assertThat(response.unit()).isEqualTo("METER");
	}

	@Test
	@DisplayName("6개 중 일부만 null 이면 그 원소만 배열에서 빠진다 (값만 비운 원소를 남기지 않는다)")
	void nullFieldsDropTheWholeElement() {
		PlanRevision revision = PlanRevision.builder().planVersion("B0-r3").status(PlanStatus.DRAFT)
				.kpi(kpi(502.0, null, 0, null, 91.6, 1740L)).build();
		when(planRevisionRepository.findByPlanVersion("B0-r3")).thenReturn(Optional.of(revision));

		PlanKpiResponse response = service.load("B0-r3");

		assertThat(response.hasKpi()).isTrue();
		assertThat(response.metrics()).extracting(PlanKpiResponse.Metric::key)
				.containsExactly("avg_move_distance", "plan_retention_rate");
		assertThat(response.highlights()).extracting(PlanKpiResponse.Highlight::key)
				.containsExactly("hard_violations", "calc_millis");
	}

	@Test
	@DisplayName("변화가 없으면 '변화 없음' 이고 tone 은 NEUTRAL 이다")
	void zeroDeltaIsNeutral() {
		PlanRevision before = PlanRevision.builder().planVersion("B0-r1").status(PlanStatus.APPROVED)
				.kpi(kpi(500.0, 1.0, 0, 10, 90.0, 100L)).build();
		PlanRevision after = PlanRevision.builder().planVersion("B0-r2").basedOnVersion("B0-r1")
				.status(PlanStatus.APPROVED).kpi(kpi(500.0, 1.0, 0, 10, 90.0, 100L)).build();
		when(planRevisionRepository.findByPlanVersion("B0-r1")).thenReturn(Optional.of(before));
		when(planRevisionRepository.findByPlanVersion("B0-r2")).thenReturn(Optional.of(after));

		PlanKpiResponse.Metric distance = metric(service.load("B0-r2"), "avg_move_distance");

		assertThat(distance.deltaPct()).isEqualTo(0.0);
		assertThat(distance.deltaLabel()).isEqualTo("변화 없음");
		assertThat(distance.tone()).isEqualTo("NEUTRAL");
		assertThat(distance.indexAfter()).isEqualTo(100.0);
	}

	@Test
	@DisplayName("나빠지면 WARN 이다 — better 방향에 따라 부호 해석이 뒤집힌다")
	void worseningIsWarn() {
		PlanRevision before = PlanRevision.builder().planVersion("B0-r1").status(PlanStatus.APPROVED)
				.kpi(kpi(500.0, 1.0, 0, 10, 90.0, 100L)).build();
		PlanRevision after = PlanRevision.builder().planVersion("B0-r2").basedOnVersion("B0-r1")
				.status(PlanStatus.APPROVED).kpi(kpi(600.0, 1.0, 0, 10, 81.0, 100L)).build();
		when(planRevisionRepository.findByPlanVersion("B0-r1")).thenReturn(Optional.of(before));
		when(planRevisionRepository.findByPlanVersion("B0-r2")).thenReturn(Optional.of(after));

		PlanKpiResponse response = service.load("B0-r2");

		// 이동거리는 늘어나면 나쁘다.
		assertThat(metric(response, "avg_move_distance").tone()).isEqualTo("WARN");
		assertThat(metric(response, "avg_move_distance").deltaLabel()).isEqualTo("20% 증가");
		// 유지율은 줄어들면 나쁘다.
		assertThat(metric(response, "plan_retention_rate").tone()).isEqualTo("WARN");
		assertThat(metric(response, "plan_retention_rate").deltaLabel()).isEqualTo("10% 감소");
	}

	@Test
	@DisplayName("반올림은 소수 첫째 자리에서 한 번만 — 은행가 반올림이 아니다")
	void roundsHalfUpToOneDecimal() {
		// 0.05 는 은행가 반올림이면 0.0 이 된다. Math.round 는 0.1 로 올린다.
		assertThat(PlanKpiService.round1(0.05)).isEqualTo(0.1);
		assertThat(PlanKpiService.round1(-38.177)).isEqualTo(-38.2);
		assertThat(PlanKpiService.round1(105.4085)).isEqualTo(105.4);

		// 라벨의 정수 퍼센트는 반올림 결과라 "0% 감소" 가 나올 수 있다. 이걸 "변화 없음" 으로
		// 뭉개지 않는다 — 부호는 남아 있어야 화살표 방향(tone)과 문구가 어긋나지 않는다.
		assertThat(PlanKpiService.deltaLabel(-0.4)).isEqualTo("0% 감소");
		assertThat(PlanKpiService.deltaLabel(-0.5)).isEqualTo("1% 감소");
		// 정확히 0 일 때만 "변화 없음" 이다.
		assertThat(PlanKpiService.deltaLabel(0.0)).isEqualTo("변화 없음");
	}

	@Test
	@DisplayName("latest 는 가장 최근 승인 판을, 승인 판이 없으면 가장 최근 판을 가리킨다")
	void latestAliasResolvesApprovedFirst() {
		PlanRevision approved = PlanRevision.builder().planVersion("B0-rAPP").status(PlanStatus.APPROVED)
				.kpi(kpi(502.0, 1.24, 0, 42, 91.6, 1740L)).build();
		when(planRevisionRepository.findFirstByStatusOrderByApprovedAtDesc(PlanStatus.APPROVED))
				.thenReturn(Optional.of(approved));

		// 응답에는 별칭이 아니라 실제로 해석된 판 번호가 담긴다 — 프론트가 이걸로 다음 요청을 건다.
		assertThat(service.load("latest").planVersion()).isEqualTo("B0-rAPP");

		when(planRevisionRepository.findFirstByStatusOrderByApprovedAtDesc(PlanStatus.APPROVED))
				.thenReturn(Optional.empty());
		PlanRevision draft = PlanRevision.builder().planVersion("B0-rDRAFT").status(PlanStatus.DRAFT).build();
		when(planRevisionRepository.findAllByOrderByIdDesc()).thenReturn(List.of(draft));

		assertThat(service.load("latest").planVersion()).isEqualTo("B0-rDRAFT");
	}

	@Test
	@DisplayName("판이 하나도 없으면 latest 도 404 다")
	void latestThrowsWhenThereIsNoPlanAtAll() {
		when(planRevisionRepository.findFirstByStatusOrderByApprovedAtDesc(PlanStatus.APPROVED))
				.thenReturn(Optional.empty());
		when(planRevisionRepository.findAllByOrderByIdDesc()).thenReturn(List.of());

		assertThatThrownBy(() -> service.load("latest"))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("계획이 아직 하나도 없습니다");
	}

	@Test
	@DisplayName("없는 판 번호는 404 다")
	void unknownPlanVersionThrowsNotFound() {
		when(planRevisionRepository.findByPlanVersion("B0-rZZZZZZ")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.load("B0-rZZZZZZ"))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("계획을 찾을 수 없습니다: B0-rZZZZZZ");
	}
}
