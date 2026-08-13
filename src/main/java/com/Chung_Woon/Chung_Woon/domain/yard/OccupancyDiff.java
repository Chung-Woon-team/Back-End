package com.Chung_Woon.Chung_Woon.domain.yard;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 사진 한 장(batchId)을 지금 DB 와 대조한 결과 전체. {@link #diffs()} 는 확정(confirm) 단계가
 * 실제로 뭘 바꿀지 정하는 데만 쓰고, {@code /check} API 응답에는 개수만 나간다
 * ({@link com.Chung_Woon.Chung_Woon.api.dto.OccupancyCheckResponse}).
 */
public record OccupancyDiff(
		String batchId,
		List<SlotDiff> diffs,
		LocalDateTime capturedAt,
		double confidence,
		boolean requiresConfirmation
) {
}
