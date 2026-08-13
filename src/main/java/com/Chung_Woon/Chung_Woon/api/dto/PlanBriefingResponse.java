package com.Chung_Woon.Chung_Woon.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 담당자용 브리핑. {@code GET}(조회) 과 {@code POST}(생성) 가 <b>같은 모양</b>을 돌려준다 —
 * 프론트가 POST 응답을 그대로 화면에 반영하고 재조회하지 않아도 되게 하기 위해서다.
 *
 * <p>{@code briefing} 은 {@code \n} 으로 줄바꿈된 통짜 문자열이다. 프론트는
 * {@code white-space: pre-line} 으로 그대로 출력한다. 반면 <b>"확인이 필요한 지점"은 구조화해서</b>
 * {@code confirmations} 로 따로 준다 — 경고 배지로 눈에 띄게 그려야 하기 때문이다.
 *
 * <p>브리핑이 아직 없어도 404 가 아니라 200 이다({@code state: NOT_GENERATED}). 이때
 * {@code briefing}/{@code source}/{@code generated_at} 키가 <b>통째로 빠진다</b>
 * (전역 {@code non_null}).
 */
@Schema(description = "판 브리핑 — 문장 한 덩어리 + 구조화된 확인 항목")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PlanBriefingResponse(

		@Schema(description = "실제로 해석된 판 번호", example = "B0-r7f3a91")
		@JsonProperty("plan_version") String planVersion,

		@Schema(allowableValues = {"GENERATED", "NOT_GENERATED"}, example = "GENERATED")
		@JsonProperty("state") String state,

		@Schema(example = "생성됨")
		@JsonProperty("state_label") String stateLabel,

		@Schema(description = "`FALLBACK` 은 Gemini 없이 수치만으로 조립된 문장이다. 생성 전이면 키가 빠진다.",
				allowableValues = {"AI", "FALLBACK"}, example = "AI")
		@JsonProperty("source") String source,

		@Schema(example = "AI 생성")
		@JsonProperty("source_label") String sourceLabel,

		@Schema(description = "브리핑을 만든 시각(초까지). 생성 전이면 키가 빠진다.",
				example = "2026-08-13T14:32:07")
		@JsonProperty("generated_at") LocalDateTime generatedAt,

		@Schema(description = "`\\n` 로 줄바꿈된 통짜 문자열. 숫자는 전부 저장된 KPI 에서만 나온다.",
				example = "B02 블록 폐쇄로 42대가 재배치되었습니다.\n- 평균 이동거리: 812 → 502m (38% 감소)")
		@JsonProperty("briefing") String briefing,

		@Schema(description = "경고 배지로 그릴 항목. 없으면 빈 배열(키는 남는다).")
		@JsonProperty("confirmations") List<Confirmation> confirmations
) {

	/**
	 * 확인이 필요한 지점 하나. <b>이 목록은 자바가 결정론적으로 만든다</b> — 파이썬이 돌려준
	 * confirmations 는 버린다. 그래야 양쪽 구현이 어긋나도 공개 응답이 흔들리지 않는다.
	 */
	@Schema(description = "확인이 필요한 지점 하나")
	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record Confirmation(

			@Schema(description = "`OBSERVATION_MISMATCH` 는 코드값만 예약해 뒀다 — 사진↔계획 대조 결과를 "
					+ "판에 연결하는 저장 경로가 아직 없어 v1 에서는 발생하지 않는다.",
					allowableValues = {"HARD_VIOLATION", "RETENTION_DROP", "LONG_MOVE", "NO_BASELINE",
							"OBSERVATION_MISMATCH"},
					example = "LONG_MOVE")
			@JsonProperty("code") String code,

			@Schema(allowableValues = {"WARN", "INFO"}, example = "WARN")
			@JsonProperty("severity") String severity,

			@Schema(description = "화면에 그대로 출력할 한국어 문장",
					example = "V-0182 의 이동거리가 1284m 로 이 판 평균(502m)의 2배를 넘습니다.")
			@JsonProperty("message") String message,

			@Schema(description = "담당자가 할 일. 없으면 키가 빠진다.",
					example = "더 가까운 대체 슬롯이 있는지 확인하세요.")
			@JsonProperty("action_hint") String actionHint,

			@Schema(description = "관련 슬롯. 없으면 키가 빠진다.", example = "B03-R12-C04")
			@JsonProperty("slot_id") String slotId
	) {
	}
}
