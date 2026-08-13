package com.Chung_Woon.Chung_Woon.ai.dto;

import com.Chung_Woon.Chung_Woon.domain.observation.ObservationSource;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 파이썬 {@code POST /internal/extract/grid} 의 응답 = {@code autoyard.schemas.GridObservation} 을
 * 그대로 옮긴 것. block_id 가 없다 — 야드 전체 사진 한 장이라 칸마다 블록은 좌표로 유도한다
 * ({@link com.Chung_Woon.Chung_Woon.domain.yard.YardGrid#blockAt}).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GridObservationResponse(
		ObservationSource sourceType,
		LocalDateTime capturedAt,
		List<GridCell> grid,
		Double confidence,
		Boolean requiresConfirmation
) {

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record GridCell(
			Integer row,
			Integer col,
			Boolean occupied
	) {
	}
}
