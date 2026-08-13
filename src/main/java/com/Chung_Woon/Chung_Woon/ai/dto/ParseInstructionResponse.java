package com.Chung_Woon.Chung_Woon.ai.dto;

import com.Chung_Woon.Chung_Woon.domain.instruction.ConstraintPriority;
import com.Chung_Woon.Chung_Woon.domain.instruction.ConstraintType;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 파이썬 {@code POST /internal/parse} 응답 = {@code app.routers.parse.ParseResponse} 를 그대로 옮긴 것.
 *
 * <p>{@code thread_id} 는 지금은 자리만 잡아둔 값이다(파이썬 쪽 LangGraph 그래프가 아직 없음) —
 * 그래도 DB 에는 보관해 둔다. 나중에 그래프가 붙으면 이 값으로 {@code /internal/resume} 을 호출한다.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ParseInstructionResponse(
		String threadId,
		Result result
) {

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record Result(
			String instructionId,
			List<ParsedConstraint> constraints,
			List<String> unresolved,
			Boolean requiresConfirmation
	) {
	}

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record ParsedConstraint(
			ConstraintType type,
			Target target,
			Map<String, Object> value,
			TimeWindow timeWindow,
			ConstraintPriority priority,
			Double confidence
	) {
		// constraint_id 는 여기 없다 — 파이썬이 만들지 않는다(gemini_client.py 참고).
		// 스프링이 PlanConstraint 를 저장할 때 자기 규칙으로 채번한다.
	}

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record Target(
			List<String> blockIds,
			String attribute,
			List<String> values,
			List<String> vehicleIds,
			Map<String, Object> filter
	) {
	}

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record TimeWindow(
			LocalDateTime start,
			LocalDateTime end
	) {
	}
}
