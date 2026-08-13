package com.Chung_Woon.Chung_Woon.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 관제 콘솔 대시보드 한 화면에 필요한 값 전부. 화면 하나 = 요청 하나다.
 *
 * <p><b>필드명을 {@code @JsonProperty} 로 하나하나 박아 뒀다.</b> 계약(API_CONTRACT.md 규칙 1)이
 * snake_case 인데, 클래스에 {@code @JsonNaming} 만 붙이면 자바 record 에는 런타임에 적용되지 않는다
 * (스웨거 문서만 snake_case 로 보이고 실제 응답은 camelCase 로 나가는 사고가 이미 있었다).
 *
 * <p>표시 문구(`status_label`, `note`)는 서버가 만든다. 프론트가 depth·재취급 같은 도메인 개념을
 * 몰라도 화면을 그릴 수 있어야 한다는 FRONTEND_CONTRACT.md 원칙을 따른 것이다.
 */
public record DashboardResponse(
		@JsonProperty("generated_at") LocalDateTime generatedAt,
		@JsonProperty("summary") Summary summary,
		@JsonProperty("blocks") List<BlockStatus> blocks,
		@JsonProperty("recent_activities") List<Activity> recentActivities,
		@JsonProperty("recent_instructions") List<RecentInstruction> recentInstructions,
		@JsonProperty("ai_engine") AiEngine aiEngine
) {

	/** 상단 카드 4개. */
	public record Summary(
			@JsonProperty("vehicles_in_yard") long vehiclesInYard,
			/** 오늘 들어온 대수. 카드의 "+N" 배지에 쓴다. */
			@JsonProperty("vehicles_arrived_today") long vehiclesArrivedToday,
			@JsonProperty("pending_constraints") long pendingConstraints,
			@JsonProperty("available_slots") long availableSlots,
			@JsonProperty("total_slots") long totalSlots,
			/** 점유율(%). 소수 첫째 자리. */
			@JsonProperty("occupancy_pct") double occupancyPct,
			/** 야드 혼잡도. `NORMAL` / `BUSY` / `CONGESTED`. */
			@JsonProperty("congestion") String congestion,
			@JsonProperty("congestion_label") String congestionLabel,
			/** 최신 판의 평균 이동거리(m). 판이 없으면 null. */
			@JsonProperty("avg_move_distance_meters") Double avgMoveDistanceMeters,
			/** 직전 판 대비 증감률(%). 비교 대상이 없으면 null. */
			@JsonProperty("avg_move_distance_delta_pct") Double avgMoveDistanceDeltaPct
	) {
	}

	/** 블록 카드 4개. */
	public record BlockStatus(
			@JsonProperty("block_id") String blockId,
			@JsonProperty("zone_code") String zoneCode,
			/** `OPERATING` / `CLOSED` / `RELOCATING` */
			@JsonProperty("status") String status,
			@JsonProperty("status_label") String statusLabel,
			@JsonProperty("occupied") long occupied,
			@JsonProperty("capacity") long capacity,
			/** "도색작업으로 폐쇄", "16대 이동 진행" 같은 한 줄 설명. */
			@JsonProperty("note") String note
	) {
	}

	/** 최근 활동 피드. */
	public record Activity(
			/** `CONSTRAINT_APPROVED` / `CONSTRAINT_REJECTED` / `PLAN_APPROVED` / `INSTRUCTION_CREATED` */
			@JsonProperty("type") String type,
			@JsonProperty("title") String title,
			@JsonProperty("description") String description,
			@JsonProperty("occurred_at") LocalDateTime occurredAt
	) {
	}

	/** 최근 지시 테이블. */
	public record RecentInstruction(
			@JsonProperty("instruction_id") String instructionId,
			@JsonProperty("author") String author,
			/** 지시 원문을 한 줄로 줄인 것. */
			@JsonProperty("summary") String summary,
			/** `APPROVED` / `PENDING_REVIEW` / `REJECTED` / `NOT_PARSED` */
			@JsonProperty("status") String status,
			@JsonProperty("status_label") String statusLabel,
			@JsonProperty("created_at") LocalDateTime createdAt
	) {
	}

	/** 좌측 하단 "AI 엔진 상태". */
	public record AiEngine(
			/** `UP` / `DOWN` / `DEGRADED` */
			@JsonProperty("status") String status,
			@JsonProperty("status_label") String statusLabel,
			/** Gemini 키가 실제로 들어가 있는지. false 면 폴백 더미가 나온다. */
			@JsonProperty("gemini_enabled") Boolean geminiEnabled
	) {
	}
}
