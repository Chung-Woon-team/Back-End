package com.Chung_Woon.Chung_Woon.ai.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;
import java.util.Map;

/**
 * 파이썬 {@code POST /internal/brief} 응답 = {@code app.routers.plan.BriefResponse}.
 *
 * <p>{@code source} 는 폴백 신호다 — {@code BriefResponse} 에는 다른 스키마들의 {@code confidence}
 * 에 해당하는 필드가 없어서 이 필드가 그 역할을 한다. {@code "FALLBACK"} 은 Gemini 없이
 * 결정론적 템플릿만으로 조립된 문장이라는 뜻이고, 그래도 200 이다.
 *
 * <p>{@code confirmations} 는 <b>자바가 보낸 것을 그대로 되돌려받는 에코</b>라 쓰지 않고 버린다
 * (자바가 만든 목록이 유일한 정본 — {@code PlanBriefingService} 참고). 계약상 파이썬이 반드시
 * 채워 보내므로 필드는 받아 두고 로그에만 쓴다.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record BriefResponse(
		String briefing,
		List<Map<String, Object>> confirmations,
		String source
) {
}
