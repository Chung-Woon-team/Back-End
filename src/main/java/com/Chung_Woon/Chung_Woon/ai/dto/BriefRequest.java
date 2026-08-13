package com.Chung_Woon.Chung_Woon.ai.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * 파이썬 {@code POST /internal/brief} 요청 = {@code app.routers.plan.BriefRequest} 를 그대로 옮긴 것.
 *
 * <p><b>{@code kpi} 6필드는 절대 null 로 보내지 않는다.</b> 파이썬 {@code PlanKpi} 는
 * {@code extra="forbid"} + 6필드 전부 필수라 null 이 하나라도 섞이면 422 로 튕긴다 —
 * {@code PlanBriefingService} 가 호출 전에 400 으로 막는다.
 *
 * <p>{@code kpiBefore} 는 null 이거나 6필드가 전부 채워진 객체다. <b>부분적으로 채워진 것은 보내지
 * 않는다</b>(같은 422 이유).
 *
 * <p>{@code moves} 는 앞에서부터 최대 200건만 싣는다 — 500대 판에서 요청이 비대해지는 걸 막는다.
 * 파이썬은 이 배열의 개수를 세지 않고 대수는 {@code kpi.changedVehicles} 를 정본으로 쓰므로,
 * 잘라도 문장의 숫자가 틀어지지 않는다.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record BriefRequest(
		Kpi kpi,
		Kpi kpiBefore,
		List<Move> moves,
		List<ConsistencyIssue> consistencyIssues
) {

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record Kpi(
			Double avgMoveDistance,
			Double rehandleProxy,
			Integer hardViolations,
			Integer changedVehicles,
			Double planRetentionRate,
			Long calcMillis
	) {
	}

	/**
	 * {@code path} 는 보내지 않는다 — 좌표열은 저장되지 않아 DB 에 없다
	 * ({@code ReplanExecutionService} 주석 참고).
	 */
	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record Move(
			String vehicleId,
			/** 신규 입고면 null. aiObjectMapper 가 NON_NULL 이라 이 경우 키가 빠진다. */
			String fromSlot,
			String toSlot,
			int sequence,
			String reason,
			Double distanceMeters
	) {
	}

	/** 코드가 대조해서 넘긴 "확인이 필요한 지점". AI 가 판정하지 않는다. */
	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record ConsistencyIssue(
			String code,
			String severity,
			String message,
			String actionHint,
			String slotId
	) {
	}
}
