package com.Chung_Woon.Chung_Woon.api.dto;

import com.Chung_Woon.Chung_Woon.domain.plan.PlanStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 판 하나의 KPI 전후 비교. 장표 6·9쪽 막대그래프가 {@code metrics[].index_before / index_after} 다.
 *
 * <p><b>수치 6개는 서버가 계산하지 않는다</b> — 재배치 시점에 파이썬이 계산해 {@code plan_revision} 에
 * 저장해 둔 값을 읽기만 한다. 여기서 새로 만드는 건 표시용 파생값(index / delta / label / tone)뿐이다.
 *
 * <p>{@code @JsonNaming} 과 {@code @JsonProperty} 를 둘 다 달았다. 런타임 케이싱은 전역
 * {@code spring.jackson.property-naming-strategy: SNAKE_CASE} 가 이미 처리하므로 중복이지만,
 * springdoc 은 Jackson2 애노테이션만 읽어서 <b>애노테이션이 없으면 스웨거 문서에만 camelCase 로
 * 표시되고 프론트가 조용히 null 을 받는다</b>(CreatePlanRequest 가 이미 그 상태다).
 *
 * <p>전역 {@code default-property-inclusion: non_null} 이라 값이 없으면 <b>키가 통째로 빠진다.</b>
 * 비교할 이전 판이 없으면 {@code before}·{@code index_*}·{@code delta_*} 키가 아예 없다.
 */
@Schema(description = "판 KPI 전후 비교 — 지수·증감문구·tone 까지 서버가 완성해서 준다")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PlanKpiResponse(

		@Schema(description = "실제로 해석된 판 번호. `latest` 로 요청해도 여기엔 실제 번호가 온다.",
				example = "B0-r7f3a91")
		@JsonProperty("plan_version") String planVersion,

		@Schema(description = "비교 기준이 된 이전 판. baseline 이면 **키 자체가 빠진다**.",
				example = "B0-r1c2d34")
		@JsonProperty("based_on_version") String basedOnVersion,

		@Schema(allowableValues = {"DRAFT", "PENDING_APPROVAL", "APPROVED", "REJECTED"}, example = "APPROVED")
		@JsonProperty("status") PlanStatus status,

		@Schema(description = "화면에 그대로 출력할 한국어 상태명", example = "승인됨")
		@JsonProperty("status_label") String statusLabel,

		@Schema(description = "false 면 metrics/highlights 가 빈 배열이다.", example = "true")
		@JsonProperty("has_kpi") boolean hasKpi,

		@Schema(description = "전후 비교가 가능한가. false 면 프론트는 막대 하나(after)만 그린다.",
				example = "true")
		@JsonProperty("comparable") boolean comparable,

		@Schema(description = "거리 축 라벨용. 항상 `METER` — 파이썬이 meters_per_cell 을 이미 곱해 "
				+ "미터로 내려준다.", allowableValues = {"METER"}, example = "METER")
		@JsonProperty("unit") String unit,

		@Schema(description = "항상 `m`", example = "m")
		@JsonProperty("unit_label") String unitLabel,

		@Schema(description = "막대그래프용 지표 3개. **배열 순서가 고정**이라 프론트가 정렬하지 않는다.")
		@JsonProperty("metrics") List<Metric> metrics,

		@Schema(description = "단독 수치 3개. 전후 비교 없이 값 하나와 tone 만 있다.")
		@JsonProperty("highlights") List<Highlight> highlights
) {

	/** 지표 하나. 값이 null 인 지표는 <b>원소 자체가 배열에서 빠진다</b>(값만 비우지 않는다). */
	@Schema(description = "전후 비교 지표 하나")
	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record Metric(

			@Schema(allowableValues = {"avg_move_distance", "rehandle_proxy", "plan_retention_rate"},
					example = "avg_move_distance")
			@JsonProperty("key") String key,

			@Schema(description = "화면에 그대로 출력할 한국어 지표명", example = "평균 이동거리")
			@JsonProperty("label") String label,

			@Schema(allowableValues = {"METER", "DEPTH", "PERCENT"}, example = "METER")
			@JsonProperty("unit") String unit,

			@Schema(example = "m")
			@JsonProperty("unit_label") String unitLabel,

			@Schema(description = "이전 판 값. 비교 불가면 **키가 빠진다**.", example = "812.0")
			@JsonProperty("before") Double before,

			@Schema(description = "이 판 값", example = "502.0")
			@JsonProperty("after") Double after,

			@Schema(description = "항상 100.0. 비교 불가면 키가 빠진다.", example = "100.0")
			@JsonProperty("index_before") Double indexBefore,

			@Schema(description = "`after / before * 100`, 소수 1자리. 장표 9쪽 막대그래프가 이 값이다.",
					example = "61.8")
			@JsonProperty("index_after") Double indexAfter,

			@Schema(description = "`after - before`, 소수 1자리", example = "-310.0")
			@JsonProperty("delta_abs") Double deltaAbs,

			@Schema(description = "증감률(%), 소수 1자리, 부호 있음", example = "-38.2")
			@JsonProperty("delta_pct") Double deltaPct,

			@Schema(description = "화면에 그대로 출력할 증감 문구", example = "38% 감소")
			@JsonProperty("delta_label") String deltaLabel,

			@Schema(description = "어느 쪽이 좋은 값인지. 화살표 방향·색 결정용.",
					allowableValues = {"LOWER", "HIGHER"}, example = "LOWER")
			@JsonProperty("better") String better,

			@Schema(allowableValues = {"GOOD", "WARN", "NEUTRAL"}, example = "GOOD")
			@JsonProperty("tone") String tone
	) {
	}

	/** 단독 수치 하나. 값이 null 이면 <b>원소 자체가 배열에서 빠진다</b>. */
	@Schema(description = "단독 수치 하나")
	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record Highlight(

			@Schema(allowableValues = {"hard_violations", "changed_vehicles", "calc_millis"},
					example = "hard_violations")
			@JsonProperty("key") String key,

			@Schema(example = "Hard 제약 위반")
			@JsonProperty("label") String label,

			@Schema(description = "저장된 KPI 값 그대로", example = "0")
			@JsonProperty("value") Number value,

			@Schema(allowableValues = {"COUNT", "VEHICLE", "MILLIS"}, example = "COUNT")
			@JsonProperty("unit") String unit,

			@Schema(example = "건")
			@JsonProperty("unit_label") String unitLabel,

			@Schema(allowableValues = {"GOOD", "WARN", "NEUTRAL"}, example = "GOOD")
			@JsonProperty("tone") String tone
	) {
	}
}
