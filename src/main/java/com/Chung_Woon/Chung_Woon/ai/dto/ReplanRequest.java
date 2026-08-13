package com.Chung_Woon.Chung_Woon.ai.dto;

import com.Chung_Woon.Chung_Woon.domain.instruction.ConstraintPriority;
import com.Chung_Woon.Chung_Woon.domain.instruction.ConstraintStatus;
import com.Chung_Woon.Chung_Woon.domain.instruction.ConstraintType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 파이썬 {@code POST /internal/replan} 요청 = {@code app.routers.plan.ReplanRequest} 를 그대로 옮긴 것.
 *
 * <p>{@code constraints} 는 승인된 것만 넣는다 — 파이썬 쪽이 {@code PENDING_REVIEW}/{@code REJECTED}
 * 가 섞여 들어오면 422 로 거절한다({@code app/routers/plan.py} 참고).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ReplanRequest(
		String basePlanVersion,
		List<ConstraintPayload> constraints,
		YardState yardState,
		List<VehiclePayload> vehicles
) {

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record ConstraintPayload(
			String constraintId,
			ConstraintType type,
			// PlanConstraint.targetJson/valueJson 은 이미 파이썬이 준 snake_case 모양 그대로
			// 저장돼 있어서(InstructionParsingService 참고), 트리로 파싱해 그대로 실어 보낸다.
			JsonNode target,
			JsonNode value,
			TimeWindow timeWindow,
			ConstraintPriority priority,
			Double confidence,
			ConstraintStatus status
	) {
	}

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record TimeWindow(LocalDateTime start, LocalDateTime end) {
	}

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record YardState(
			List<BlockPayload> blocks,
			List<SlotPayload> slots,
			Map<String, String> placements
	) {
	}

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record BlockPayload(String blockId, boolean closed) {
	}

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record SlotPayload(String slotId, String blockId, String status) {
	}

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record VehiclePayload(
			String vehicleId,
			String status,
			String priority,
			String nextMode,
			LocalDateTime departureCutoffAt,
			String brand,
			Integer dischargeSequence
	) {
	}
}
