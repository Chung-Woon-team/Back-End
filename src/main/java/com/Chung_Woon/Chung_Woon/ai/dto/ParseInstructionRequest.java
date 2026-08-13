package com.Chung_Woon.Chung_Woon.ai.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 파이썬 {@code POST /internal/parse} 요청 = {@code app.routers.parse.ParseRequest} 를 그대로 옮긴 것.
 *
 * <p>{@code instructionId} 는 스프링이 {@code POST /api/instructions} 에서 먼저 발급한 값을 실어
 * 보낸다 — 그래야 AI 가 돌려주는 {@code ParseResult.instruction_id} 가 이미 저장된 행과 일치한다.
 * {@code validBlockIds}/{@code validBrands}/{@code validZones} 는 스프링이 자기 DB(Block, Vehicle)
 * 에서 직접 조회해서 넘긴다 — 파이썬은 뭐가 유효한 값인지 알 방법이 없다.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ParseInstructionRequest(
		String rawText,
		String author,
		String instructionId,
		LocalDateTime referenceDatetime,
		List<String> validBlockIds,
		List<String> validBrands,
		List<String> validZones
) {
}
