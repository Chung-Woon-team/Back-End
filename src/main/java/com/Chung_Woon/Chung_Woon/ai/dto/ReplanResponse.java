package com.Chung_Woon.Chung_Woon.ai.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;
import java.util.Map;

/**
 * 파이썬 {@code POST /internal/replan} 응답 = {@code autoyard.schemas.ReplanResult} 를 그대로 옮긴 것.
 *
 * <p>{@code placements} 는 이 판의 <b>최종</b> 배치 전체(그대로 있던 차 포함)다. {@code moves} 는
 * 실제로 옮긴 차만 — {@code MoveTask} 로 그대로 저장한다.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ReplanResponse(
		String planVersion,
		String basedOnVersion,
		Map<String, String> placements,
		List<Move> moves,
		PlanKpi kpi,
		PlanKpi kpiBefore,
		List<String> unplaced
) {

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record Move(
			String vehicleId,
			String fromSlot,
			String toSlot,
			Integer sequence,
			String reason,
			Double distanceMeters,
			// 프론트 애니메이션용. DB 에는 안 남긴다(docs/API_CONTRACT.md §4 "좌표열은 시각화용,
			// 저장 안 함") - ReplanExecutionService 가 MoveTask 로 저장할 때 이 필드는 버리고,
			// 방금 계산한 이 응답에만 실어서 컨트롤러가 그대로 돌려준다.
			List<PathStep> path
	) {
	}

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record PathStep(Integer row, Integer col, Integer t) {
	}

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record PlanKpi(
			Double avgMoveDistance,
			Double rehandleProxy,
			Integer hardViolations,
			Integer changedVehicles,
			Double planRetentionRate,
			Long calcMillis
	) {
	}
}
