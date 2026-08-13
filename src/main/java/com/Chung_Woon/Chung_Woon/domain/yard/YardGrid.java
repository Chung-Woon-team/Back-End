package com.Chung_Woon.Chung_Woon.domain.yard;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 야드 격자. 실제 야드 항공사진 기준이다.
 *
 * <pre>
 *   행: 4 +  5 + 4 +  5 + 4 = 22
 *   열: 4 + 17 + 4 + 17 + 4 = 46
 *
 *   열 →  0    4              21   25              42  46
 *   행 0 ┌───────────────────────────────────────────┐
 *        │            외곽 도로 (폭 4)                │
 *      4 ├────┬──────────────┬────┬──────────────┬───┤
 *        │도로│  B01  5×17   │통로│  B02  5×17   │도로│
 *      9 ├────┼──────────────┼────┼──────────────┼───┤
 *        │            십자 통로 (폭 4)                │
 *     13 ├────┼──────────────┼────┼──────────────┼───┤
 *        │도로│  B03  5×17   │통로│  B04  5×17   │도로│
 *     18 ├────┴──────────────┴────┴──────────────┴───┤
 *        │            외곽 도로 (폭 4)                │
 *     22 └───────────────────────────────────────────┘
 * </pre>
 *
 * <p>주차칸 5×17×4 = 340, 도로칸 22×46 − 340 = 672.
 *
 * <p><b>정사각형이 아니다.</b> 블록이 가로로 길다(17칸 폭 × 5칸 깊이) — 차 한 대가
 * 폭 2.5m · 길이 5m 라 사진에서도 블록이 가로로 긴 직사각형으로 보인다.
 * 그래서 행 수와 열 수를 따로 둔다.
 *
 * <p><b>도로는 엔티티가 아니다.</b> 672칸에 상태가 없어서 테이블로 두면 얻는 게 없다.
 * 경로 알고리즘이 필요로 하는 "지나갈 수 있는 칸" 은 {@link #isRoad(int, int)} 로 판정한다.
 *
 * <p><b>depth 는 가장 가까운 도로 기준이다.</b> 블록이 네 면 모두 도로에 닿아 있어서
 * 한 레인(세로 5칸)을 위 3칸 / 아래 2칸으로 갈라 각각 0부터 센다. 어느 쪽인지는
 * {@link AccessSide} 가 들고 있다.
 */
public final class YardGrid {

	/** 외곽 도로와 십자 통로의 폭. */
	public static final int ROAD_WIDTH = 4;

	/** 블록 하나의 행 수(깊이). 사진 기준 5. */
	public static final int BLOCK_ROWS = 5;

	/** 블록 하나의 열 수(폭). 사진 기준 17. */
	public static final int BLOCK_COLS = 17;

	public static final int BLOCK_COUNT = 4;

	/** 야드 전체 행 수. 4 + 5 + 4 + 5 + 4 = 22. */
	public static final int ROW_COUNT = ROAD_WIDTH * 3 + BLOCK_ROWS * 2;

	/** 야드 전체 열 수. 4 + 17 + 4 + 17 + 4 = 46. */
	public static final int COL_COUNT = ROAD_WIDTH * 3 + BLOCK_COLS * 2;

	/** 위/왼쪽 블록이 시작하는 좌표. 외곽 도로 바로 다음 칸. */
	public static final int FIRST_BLOCK_ORIGIN = ROAD_WIDTH;

	/** 아래쪽 블록이 시작하는 행. 외곽 도로 + 블록 + 십자 통로 다음. */
	public static final int SECOND_BLOCK_ORIGIN_ROW = ROAD_WIDTH + BLOCK_ROWS + ROAD_WIDTH;

	/** 오른쪽 블록이 시작하는 열. */
	public static final int SECOND_BLOCK_ORIGIN_COL = ROAD_WIDTH + BLOCK_COLS + ROAD_WIDTH;

	/** 블록 하나의 레인 수 = 세로 열 17개. */
	public static final int LANES_PER_BLOCK = BLOCK_COLS;

	/**
	 * 한 레인이 한쪽 도로에서 갖는 최대 깊이. depth 는 0 ~ (이 값 - 1).
	 * 5칸을 위 3 / 아래 2 로 가르므로 3 이다(홀수라 위쪽이 한 칸 더 깊다).
	 */
	public static final int DEPTH_PER_LANE = (BLOCK_ROWS + 1) / 2;

	public static final int SLOTS_PER_BLOCK = BLOCK_ROWS * BLOCK_COLS;

	/** 주차 가능한 칸 수. 340. */
	public static final int SLOT_COUNT = BLOCK_COUNT * SLOTS_PER_BLOCK;

	/** 도로 칸 수. 672. */
	public static final int ROAD_CELL_COUNT = ROW_COUNT * COL_COUNT - SLOT_COUNT;

	private YardGrid() {
	}

	/** 야드 절대 좌표 한 칸. */
	public record Cell(int row, int col) {
	}

	public static boolean isInside(int row, int col) {
		return row >= 0 && row < ROW_COUNT && col >= 0 && col < COL_COUNT;
	}

	/** 이 칸이 속한 블록. 도로면 비어 있다. */
	public static Optional<BlockLayout> blockAt(int row, int col) {
		if (!isInside(row, col)) {
			return Optional.empty();
		}
		for (BlockLayout layout : BlockLayout.values()) {
			if (layout.contains(row, col)) {
				return Optional.of(layout);
			}
		}
		return Optional.empty();
	}

	/** 차가 지나다닐 수 있는 칸인지. 외곽 도로와 십자 통로 둘 다 true. */
	public static boolean isRoad(int row, int col) {
		return isInside(row, col) && blockAt(row, col).isEmpty();
	}

	public static boolean isSlot(int row, int col) {
		return blockAt(row, col).isPresent();
	}

	/** 경로 알고리즘이 그래프로 쓸 도로 칸 전부. 행 → 열 순서. */
	public static List<Cell> roadCells() {
		List<Cell> cells = new ArrayList<>(ROAD_CELL_COUNT);
		for (int row = 0; row < ROW_COUNT; row++) {
			for (int col = 0; col < COL_COUNT; col++) {
				if (isRoad(row, col)) {
					cells.add(new Cell(row, col));
				}
			}
		}
		return cells;
	}

	/** 슬롯 칸 전부. 블록 순서 → 행 → 열. 초기 적재가 이 순서로 만든다. */
	public static List<Cell> slotCells() {
		List<Cell> cells = new ArrayList<>(SLOT_COUNT);
		for (BlockLayout layout : BlockLayout.values()) {
			for (int row = layout.originRow(); row <= layout.lastRow(); row++) {
				for (int col = layout.originCol(); col <= layout.lastCol(); col++) {
					cells.add(new Cell(row, col));
				}
			}
		}
		return cells;
	}

	/**
	 * 슬롯 PK. 절대 좌표를 그대로 넣어서 사진 인식 결과 {@code (row, col)} 을 문자열 하나로 뒤집을 수 있게 했다.
	 * 예: {@code B01-R04-C07}
	 */
	public static String slotId(int row, int col) {
		BlockLayout layout = blockAt(row, col)
				.orElseThrow(() -> new IllegalArgumentException(
						"(%d, %d) 은(는) 도로 칸이라 슬롯이 없다".formatted(row, col)));
		return "%s-R%02d-C%02d".formatted(layout.blockId(), row, col);
	}

	/** {@link #slotId(int, int)} 의 역방향. 사진 좌표 ↔ 슬롯 매핑에 쓴다. */
	public static Cell cellOf(String slotId) {
		String[] parts = slotId.split("-");
		if (parts.length != 3 || parts[1].charAt(0) != 'R' || parts[2].charAt(0) != 'C') {
			throw new IllegalArgumentException("슬롯 ID 형식이 아니다: " + slotId);
		}
		int row = Integer.parseInt(parts[1].substring(1));
		int col = Integer.parseInt(parts[2].substring(1));
		if (!isSlot(row, col)) {
			throw new IllegalArgumentException("격자에 없는 슬롯: " + slotId);
		}
		return new Cell(row, col);
	}
}
