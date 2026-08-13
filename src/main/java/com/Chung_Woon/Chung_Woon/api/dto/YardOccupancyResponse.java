package com.Chung_Woon.Chung_Woon.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 배치·경로 알고리즘 입력용 야드 스냅샷.
 *
 * <p>알고리즘은 <b>빈 야드를 가정하지 않는다.</b> 이미 서 있는 차를 피해서 자리를 고르고
 * 경로를 그려야 하므로, "지금 어느 칸에 누가 있고 언제 어느 출구로 나가는지" 를 준다.
 *
 * <p>좌표는 알고리즘이 쓰는 {@code (x, y)} 로도 같이 준다 — {@code x = col}, {@code y = row}.
 * 슬롯 ID(`B01-R04-C07`)를 파싱하지 않아도 되게 하려는 것이다.
 */
public record YardOccupancyResponse(
		@JsonProperty("grid") Grid grid,
		@JsonProperty("blocks") List<BlockState> blocks,
		@JsonProperty("occupied") List<ParkedVehicle> occupied,
		@JsonProperty("generated_at") LocalDateTime generatedAt
) {

	public record Grid(
			/** 야드 전체 행 수. 22. */
			@JsonProperty("rows") int rows,
			/** 야드 전체 열 수. 46. 정사각형이 아니다. */
			@JsonProperty("cols") int cols,
			@JsonProperty("road_width") int roadWidth,
			/** 블록 하나의 행 수(깊이). 5. */
			@JsonProperty("block_rows") int blockRows,
			/** 블록 하나의 열 수(폭). 17. */
			@JsonProperty("block_cols") int blockCols,
			@JsonProperty("total_slots") int totalSlots
	) {
	}

	public record BlockState(
			@JsonProperty("block_id") String blockId,
			/** 알고리즘의 zone 번호. B01→1, B02→2, B03→3, B04→4. */
			@JsonProperty("zone_id") int zoneId,
			@JsonProperty("origin_row") int originRow,
			@JsonProperty("origin_col") int originCol,
			@JsonProperty("block_rows") int blockRows,
			@JsonProperty("block_cols") int blockCols,
			/** 닫힌 블록에는 <b>새로 배치하지 않는다.</b> 다만 길로 지나가는 건 된다. */
			@JsonProperty("closed") boolean closed,
			@JsonProperty("closure_reason") String closureReason
	) {
	}

	/** 이미 서 있는 차 한 대. */
	public record ParkedVehicle(
			@JsonProperty("vehicle_id") String vehicleId,
			@JsonProperty("slot_id") String slotId,
			@JsonProperty("block_id") String blockId,
			/** 야드 절대 행 (알고리즘의 y). */
			@JsonProperty("row") int row,
			/** 야드 절대 열 (알고리즘의 x). */
			@JsonProperty("col") int col,
			/** `TRUCK` / `RAIL` / `SHIP`. 어느 출구로 나가는지. 모르면 null. */
			@JsonProperty("next_mode") String nextMode,
			/** 이 시각까지는 나가야 한다. 모르면 null — 그 차는 계속 서 있는 것으로 본다. */
			@JsonProperty("departure_cutoff_at") LocalDateTime departureCutoffAt
	) {
	}
}
