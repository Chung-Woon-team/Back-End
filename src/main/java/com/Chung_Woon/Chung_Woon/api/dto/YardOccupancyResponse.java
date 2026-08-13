package com.Chung_Woon.Chung_Woon.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 야드 전체 상태. 배치·경로 알고리즘의 입력이자 야드 화면의 원본이다.
 *
 * <p>격자 구조(어느 칸이 주차칸이고 레인·depth 가 얼마인지)는 <b>코드가 정본</b>이라
 * DB 적재 여부와 무관하게 항상 340칸 전부 내려간다. 점유 상태(누가 서 있는지)만 DB 에서 온다.
 * 둘을 구분하려면 {@code grid_seeded} 를 보면 된다.
 *
 * <p>좌표는 알고리즘이 쓰는 {@code (x, y)} 그대로 준다 — {@code x = col}, {@code y = row}.
 * 슬롯 ID(`B01-R04-C07`)를 파싱하지 않아도 된다.
 */
@Schema(description = "야드 전체 상태 — 격자 · 블록 4개 · 주차칸 340개 전부")
public record YardOccupancyResponse(

		@Schema(description = "격자 크기. 코드 상수라 DB 와 무관하게 항상 같다.")
		@JsonProperty("grid") Grid grid,

		@Schema(description = "DB 에 격자가 적재됐는지. false 면 slots 의 status 는 실제 상태가 아니다 "
				+ "(전부 EMPTY 로 채워 보낸다). 서버를 재기동하면 YardLayoutInitializer 가 깐다.",
				example = "true")
		@JsonProperty("grid_seeded") boolean gridSeeded,

		@Schema(description = "점유 요약. slots 를 세지 않고도 화면 상단 숫자를 그릴 수 있다.")
		@JsonProperty("summary") Summary summary,

		@Schema(description = "블록 4개. 구조는 코드 정의(BlockLayout)가 정본이고 폐쇄 여부만 DB 에서 온다.")
		@JsonProperty("blocks") List<BlockState> blocks,

		@Schema(description = "주차칸 340개 전부. **빈 칸도 포함한다** — 화면이 격자를 통째로 그릴 수 있어야 한다.")
		@JsonProperty("slots") List<SlotState> slots,

		@JsonProperty("generated_at") LocalDateTime generatedAt
) {

	@Schema(description = "야드 격자 크기. 정사각형이 아니다 — 블록이 가로로 길다(폭 17 × 깊이 5).")
	public record Grid(
			@Schema(description = "야드 전체 행 수", example = "22")
			@JsonProperty("rows") int rows,

			@Schema(description = "야드 전체 열 수", example = "46")
			@JsonProperty("cols") int cols,

			@Schema(description = "외곽 도로와 십자 통로의 폭", example = "4")
			@JsonProperty("road_width") int roadWidth,

			@Schema(description = "블록 하나의 행 수(깊이)", example = "5")
			@JsonProperty("block_rows") int blockRows,

			@Schema(description = "블록 하나의 열 수(폭)", example = "17")
			@JsonProperty("block_cols") int blockCols,

			@Schema(description = "주차칸 총수 = 5 × 17 × 4", example = "340")
			@JsonProperty("total_slots") int totalSlots,

			@Schema(description = "도로칸 총수 = 22 × 46 − 340. 경로 알고리즘이 지나갈 수 있는 칸.",
					example = "672")
			@JsonProperty("road_cells") int roadCells
	) {
	}

	@Schema(description = "점유 요약")
	public record Summary(
			@Schema(example = "340") @JsonProperty("total") long total,
			@Schema(example = "191") @JsonProperty("occupied") long occupied,
			@Schema(description = "빈 칸 중 폐쇄되지 않은 블록에 있는 것만", example = "149")
			@JsonProperty("available") long available,
			@Schema(description = "점유율(%)", example = "56.2")
			@JsonProperty("occupancy_pct") double occupancyPct
	) {
	}

	@Schema(description = "블록 하나")
	public record BlockState(
			@Schema(example = "B01") @JsonProperty("block_id") String blockId,

			@Schema(description = "배치 알고리즘이 쓰는 zone 번호. B01→1 … B04→4", example = "1")
			@JsonProperty("zone_id") int zoneId,

			@Schema(description = "선하증권의 TARGET YARD ZONE 이 가리키는 값", example = "EV-A")
			@JsonProperty("zone_code") String zoneCode,

			@Schema(description = "블록 왼쪽 위 칸의 야드 절대 행", example = "4")
			@JsonProperty("origin_row") int originRow,

			@Schema(description = "블록 왼쪽 위 칸의 야드 절대 열", example = "4")
			@JsonProperty("origin_col") int originCol,

			@Schema(example = "5") @JsonProperty("block_rows") int blockRows,

			@Schema(example = "17") @JsonProperty("block_cols") int blockCols,

			@Schema(description = "이 블록의 주차칸 수 = 5 × 17", example = "85")
			@JsonProperty("capacity") int capacity,

			@Schema(example = "56") @JsonProperty("occupied") long occupied,

			@Schema(description = "닫힌 블록에는 새로 배치하지 않는다. 다만 길로 지나가는 것은 허용해야 한다 "
					+ "— 장애물로 만들면 안 된다.", example = "false")
			@JsonProperty("closed") boolean closed,

			@Schema(example = "도색작업") @JsonProperty("closure_reason") String closureReason
	) {
	}

	@Schema(description = "주차칸 하나. 비어 있어도 내려간다.")
	public record SlotState(
			@Schema(description = "블록-절대행-절대열", example = "B01-R04-C07")
			@JsonProperty("slot_id") String slotId,

			@Schema(example = "B01") @JsonProperty("block_id") String blockId,

			@Schema(description = "야드 절대 행. 알고리즘의 y", example = "4")
			@JsonProperty("row") int row,

			@Schema(description = "야드 절대 열. 알고리즘의 x", example = "7")
			@JsonProperty("col") int col,

			@Schema(description = "블록 안의 세로 열 번호 0~16", example = "3")
			@JsonProperty("lane") int lane,

			@Schema(description = "진입 도로에서 몇 번째인지 0~2. 0 이 도로에 바로 붙어 있다. "
					+ "재취급 비용의 근거다.", example = "0")
			@JsonProperty("depth") int depth,

			@Schema(description = "레인의 어느 쪽 도로로 들어가는지",
					allowableValues = {"NORTH", "SOUTH"}, example = "NORTH")
			@JsonProperty("access_side") String accessSide,

			@Schema(allowableValues = {"EMPTY", "OCCUPIED", "BLOCKED"}, example = "OCCUPIED")
			@JsonProperty("status") String status,

			@Schema(description = "서 있는 차. 빈 칸이면 없다.", example = "V-0001")
			@JsonProperty("vehicle_id") String vehicleId,

			@Schema(description = "어느 출구로 나가는지", allowableValues = {"TRUCK", "RAIL", "SHIP"})
			@JsonProperty("next_mode") String nextMode,

			@Schema(description = "이 시각까지는 나가야 한다. 없으면 계속 서 있는 것으로 본다.")
			@JsonProperty("departure_cutoff_at") LocalDateTime departureCutoffAt
	) {
	}
}
