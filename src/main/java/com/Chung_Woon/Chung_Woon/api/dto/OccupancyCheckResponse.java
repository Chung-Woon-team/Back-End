package com.Chung_Woon.Chung_Woon.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.LocalDateTime;

/**
 * 야드 사진 한 장과 지금 DB 상태를 대조한 결과. 슬롯별 상세 목록은 안 준다 — 몇 곳이 다른지(
 * {@code diff_count})만으로 확정(PHOTO/KEEP) 여부를 정하기 충분하다는 판단. 실제로 어느 슬롯이
 * 어떻게 바뀌는지는 {@code /confirm} 이 내부적으로 계산해서 적용한다.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record OccupancyCheckResponse(
		String batchId,
		int diffCount,
		LocalDateTime capturedAt,
		double confidence,
		boolean requiresConfirmation
) {
}
