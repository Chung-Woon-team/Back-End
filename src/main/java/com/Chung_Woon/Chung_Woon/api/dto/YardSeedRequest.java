package com.Chung_Woon.Chung_Woon.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * 데모용 야드 점유 상태 주입. 실제 야드 사진을 보고 읽은 값을 그대로 넣는 용도다.
 *
 * <p>격자(블록 4개 · 슬롯 1,936칸)는 {@code YardLayoutInitializer} 가 기동 시 이미 만들어 둔다.
 * 여기서 넣는 건 <b>점유 상태</b>뿐이다 — 어느 칸에 차가 서 있고 어느 블록이 닫혀 있는지.
 */
public record YardSeedRequest(
		/** 블록별로 몇 대를 세울지. 생략한 블록은 건드리지 않는다. */
		@JsonProperty("blocks") List<BlockOccupancy> blocks,

		/** 폐쇄할 블록. 여기 없는 블록은 폐쇄가 풀린다({@code reset} 이 true 일 때만). */
		@JsonProperty("closed_blocks") List<ClosedBlock> closedBlocks,

		/**
		 * true 면 기존 차량과 점유를 <b>전부 지우고</b> 새로 넣는다.
		 * false 면 기존 위에 덧붙인다(같은 칸은 건드리지 않음).
		 */
		@JsonProperty("reset") Boolean reset,

		/** 출차 컷오프까지 남은 시간(분) 하한. 기본 30. */
		@JsonProperty("cutoff_minutes_from") Long cutoffMinutesFrom,

		/** 출차 컷오프까지 남은 시간(분) 상한. 기본 600(10시간). */
		@JsonProperty("cutoff_minutes_to") Long cutoffMinutesTo
) {

	public long cutoffMinutesFromOrDefault() {
		return cutoffMinutesFrom == null ? 30L : cutoffMinutesFrom;
	}

	public long cutoffMinutesToOrDefault() {
		return cutoffMinutesTo == null ? 600L : cutoffMinutesTo;
	}

	public List<BlockOccupancy> blocksOrEmpty() {
		return blocks == null ? List.of() : blocks;
	}

	public List<ClosedBlock> closedBlocksOrEmpty() {
		return closedBlocks == null ? List.of() : closedBlocks;
	}

	public boolean isReset() {
		return Boolean.TRUE.equals(reset);
	}

	public record BlockOccupancy(
			@JsonProperty("block_id") String blockId,
			/** 이 블록에 세울 차량 수. 블록 용량(484)을 넘으면 400 이다. */
			@JsonProperty("occupied") int occupied,
			/** 브랜드 분포. 비우면 전부 HYUNDAI. 예: ["HYUNDAI", "GENESIS"] */
			@JsonProperty("brands") List<String> brands
	) {

		public List<String> brandsOrDefault() {
			return brands == null || brands.isEmpty() ? List.of("HYUNDAI") : brands;
		}
	}

	public record ClosedBlock(
			@JsonProperty("block_id") String blockId,
			@JsonProperty("reason") String reason
	) {
	}

	/** 전체 삭제 결과. */
	public record ClearResult(
			@JsonProperty("vehicles_removed") int vehiclesRemoved,
			@JsonProperty("slots_available") long slotsAvailable
	) {
	}

	/** 처리 결과. */
	public record Result(
			@JsonProperty("vehicles_created") int vehiclesCreated,
			@JsonProperty("vehicles_removed") int vehiclesRemoved,
			@JsonProperty("slots_occupied") long slotsOccupied,
			@JsonProperty("slots_available") long slotsAvailable,
			@JsonProperty("closed_blocks") List<String> closedBlocks
	) {

		public static Result of(int created, int removed, long occupied, long available,
				List<String> closed) {
			return new Result(created, removed, occupied, available, new ArrayList<>(closed));
		}
	}
}
