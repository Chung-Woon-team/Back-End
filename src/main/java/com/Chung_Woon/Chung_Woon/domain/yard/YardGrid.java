package com.Chung_Woon.Chung_Woon.domain.yard;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 야드 격자. 팀 도면 그대로다.
 *
 * <pre>
 *   가로·세로 모두 4 + 22 + 4 + 22 + 4 = 56 칸
 *
 *   열 →   0    4              26   30              52   56
 *   행 0  ┌────────────────────────────────────────────┐
 *         │            외곽 도로 (폭 4)                 │
 *      4  ├────┬──────────────┬────┬──────────────┬────┤
 *         │도로│  B01 22×22   │통로│  B02 22×22   │도로│
 *     26  ├────┼──────────────┼────┼──────────────┼────┤
 *         │            십자 통로 (폭 4)                 │
 *     30  ├────┼──────────────┼────┼──────────────┼────┤
 *         │도로│  B03 22×22   │통로│  B04 22×22   │도로│
 *     52  ├────┴──────────────┴────┴──────────────┴────┤
 *         │            외곽 도로 (폭 4)                 │
 *     56  └────────────────────────────────────────────┘
 * </pre>
 *
 * <p>주차칸 22×22×4 = 1,936, 도로칸 56² − 1,936 = 1,200.
 *
 * <p><b>도로는 엔티티가 아니다.</b> 1,200칸을 테이블에 넣어도 상태가 없어서 얻는 게 없다.
 * 경로 알고리즘이 필요로 하는 "지나갈 수 있는 칸" 은 {@link #isRoad(int, int)} 로 그때그때 판정한다.
 *
 * <p><b>depth 는 가장 가까운 도로 기준이다.</b> 블록이 네 면 모두 도로에 닿아 있어서
 * 한 레인(세로 22칸)을 위 11칸 / 아래 11칸으로 갈라 각각 0~10 을 매긴다. 어느 쪽인지는
 * {@link AccessSide} 가 들고 있다.
 */
public final class YardGrid {

	/** 야드 한 변의 칸 수. */
	public static final int SIZE = 56;

	/** 외곽 도로와 십자 통로의 폭. 도면의 "4칸". */
	public static final int ROAD_WIDTH = 4;

	/** 블록 한 변의 칸 수. 도면의 "22 * 22". */
	public static final int BLOCK_SIZE = 22;

	public static final int BLOCK_COUNT = 4;

	/** 위/왼쪽 블록이 시작하는 좌표. 외곽 도로 바로 다음 칸. */
	public static final int FIRST_BLOCK_ORIGIN = ROAD_WIDTH;

	/** 아래/오른쪽 블록이 시작하는 좌표. 외곽 도로 + 블록 + 십자 통로 다음 칸. */
	public static final int SECOND_BLOCK_ORIGIN = ROAD_WIDTH + BLOCK_SIZE + ROAD_WIDTH;

	/** 블록 하나의 레인 수 = 세로 열 22개. */
	public static final int LANES_PER_BLOCK = BLOCK_SIZE;

	/** 한 레인이 한쪽 도로에서 갖는 깊이. depth 는 0 ~ (이 값 - 1). */
	public static final int DEPTH_PER_LANE = BLOCK_SIZE / 2;

	public static final int SLOTS_PER_BLOCK = BLOCK_SIZE * BLOCK_SIZE;

	/** 주차 가능한 칸 수. 1,936. */
	public static final int SLOT_COUNT = BLOCK_COUNT * SLOTS_PER_BLOCK;

	/** 도로 칸 수. 1,200. */
	public static final int ROAD_CELL_COUNT = SIZE * SIZE - SLOT_COUNT;

	private YardGrid() {
	}

	/** 야드 절대 좌표 한 칸. */
	public record Cell(int row, int col) {
	}

	public static boolean isInside(int row, int col) {
		return row >= 0 && row < SIZE && col >= 0 && col < SIZE;
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
		for (int row = 0; row < SIZE; row++) {
			for (int col = 0; col < SIZE; col++) {
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
