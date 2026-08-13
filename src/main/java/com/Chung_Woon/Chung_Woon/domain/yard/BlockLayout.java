package com.Chung_Woon.Chung_Woon.domain.yard;

/**
 * 도면에 그려진 블록 4개의 고정 좌표. 야드 격자는 {@link YardGrid} 참고.
 *
 * <p>블록 자체는 DB 엔티티({@link Block})지만, "어디에 있는 22×22 인가" 는 도면에서 정해진 값이라
 * 코드에 박아 둔다. 초기 적재와 좌표 ↔ 슬롯 매핑이 둘 다 이걸 본다.
 *
 * <p>zoneCode 는 선하증권의 TARGET YARD ZONE 이 가리키는 값인데, 어느 블록이 어느 존인지는
 * 아직 팀에서 확정하지 않았다. 지금 값은 임시다.
 */
public enum BlockLayout {

	/** 좌상 */
	B01("EV-A", YardGrid.FIRST_BLOCK_ORIGIN, YardGrid.FIRST_BLOCK_ORIGIN),

	/** 우상 */
	B02("GEN-B", YardGrid.FIRST_BLOCK_ORIGIN, YardGrid.SECOND_BLOCK_ORIGIN),

	/** 좌하 */
	B03("QC-HOLD", YardGrid.SECOND_BLOCK_ORIGIN, YardGrid.FIRST_BLOCK_ORIGIN),

	/** 우하 */
	B04("HVY-D", YardGrid.SECOND_BLOCK_ORIGIN, YardGrid.SECOND_BLOCK_ORIGIN);

	private final String zoneCode;
	private final int originRow;
	private final int originCol;

	BlockLayout(String zoneCode, int originRow, int originCol) {
		this.zoneCode = zoneCode;
		this.originRow = originRow;
		this.originCol = originCol;
	}

	/** 엔티티 PK 와 같은 값. "B01" */
	public String blockId() {
		return name();
	}

	public String zoneCode() {
		return zoneCode;
	}

	/** 블록 왼쪽 위 칸의 야드 절대 행. */
	public int originRow() {
		return originRow;
	}

	/** 블록 왼쪽 위 칸의 야드 절대 열. */
	public int originCol() {
		return originCol;
	}

	public int lastRow() {
		return originRow + YardGrid.BLOCK_SIZE - 1;
	}

	public int lastCol() {
		return originCol + YardGrid.BLOCK_SIZE - 1;
	}

	public boolean contains(int row, int col) {
		return row >= originRow && row <= lastRow() && col >= originCol && col <= lastCol();
	}

	/** 레인 = 블록 안의 세로 열. 0 ~ 21. */
	public int lane(int col) {
		requireContainsCol(col);
		return col - originCol;
	}

	/** 이 자리가 위쪽 도로에서 들어가는지 아래쪽 도로에서 들어가는지. */
	public AccessSide accessSide(int row) {
		requireContainsRow(row);
		return (row - originRow) < YardGrid.DEPTH_PER_LANE ? AccessSide.NORTH : AccessSide.SOUTH;
	}

	/**
	 * 진입 도로에서 몇 번째 자리인지. 0 ~ 10 이고, 0 이 도로에 바로 붙은 칸이다.
	 * 재취급 Proxy 는 이 값을 쓴다 — depth 5 짜리를 빼려면 앞의 5대를 옮겨야 한다.
	 */
	public int depth(int row) {
		requireContainsRow(row);
		int offset = row - originRow;
		return offset < YardGrid.DEPTH_PER_LANE ? offset : (YardGrid.BLOCK_SIZE - 1 - offset);
	}

	private void requireContainsRow(int row) {
		if (row < originRow || row > lastRow()) {
			throw new IllegalArgumentException("행 %d 은(는) 블록 %s 밖이다".formatted(row, blockId()));
		}
	}

	private void requireContainsCol(int col) {
		if (col < originCol || col > lastCol()) {
			throw new IllegalArgumentException("열 %d 은(는) 블록 %s 밖이다".formatted(col, blockId()));
		}
	}

	public static BlockLayout of(String blockId) {
		for (BlockLayout layout : values()) {
			if (layout.blockId().equals(blockId)) {
				return layout;
			}
		}
		throw new IllegalArgumentException("도면에 없는 블록: " + blockId);
	}
}
