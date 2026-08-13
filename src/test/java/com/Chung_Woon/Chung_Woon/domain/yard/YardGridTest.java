package com.Chung_Woon.Chung_Woon.domain.yard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 도면(4 + 22 + 4 + 22 + 4)과 코드가 어긋나면 여기서 깨진다. */
class YardGridTest {

	@Test
	@DisplayName("격자는 56×56 이고 주차칸 1,936 · 도로칸 1,200 으로 나뉜다")
	void gridCounts() {
		assertThat(YardGrid.ROW_COUNT).isEqualTo(22);
		assertThat(YardGrid.COL_COUNT).isEqualTo(46);
		assertThat(YardGrid.ROAD_WIDTH + YardGrid.BLOCK_ROWS + YardGrid.ROAD_WIDTH
				+ YardGrid.BLOCK_ROWS + YardGrid.ROAD_WIDTH).isEqualTo(YardGrid.ROW_COUNT);
		assertThat(YardGrid.ROAD_WIDTH + YardGrid.BLOCK_COLS + YardGrid.ROAD_WIDTH
				+ YardGrid.BLOCK_COLS + YardGrid.ROAD_WIDTH).isEqualTo(YardGrid.COL_COUNT);

		assertThat(BlockLayout.values()).hasSize(YardGrid.BLOCK_COUNT);
		assertThat(YardGrid.SLOT_COUNT).isEqualTo(340);
		assertThat(YardGrid.ROAD_CELL_COUNT).isEqualTo(672);

		assertThat(YardGrid.slotCells()).hasSize(YardGrid.SLOT_COUNT);
		assertThat(YardGrid.roadCells()).hasSize(YardGrid.ROAD_CELL_COUNT);
	}

	@Test
	@DisplayName("모든 칸은 도로이거나 슬롯이고, 둘 다인 칸은 없다")
	void everyCellIsEitherRoadOrSlot() {
		int road = 0;
		int slot = 0;
		for (int row = 0; row < YardGrid.ROW_COUNT; row++) {
			for (int col = 0; col < YardGrid.COL_COUNT; col++) {
				assertThat(YardGrid.isRoad(row, col)).isNotEqualTo(YardGrid.isSlot(row, col));
				if (YardGrid.isRoad(row, col)) {
					road++;
				} else {
					slot++;
				}
			}
		}
		assertThat(road).isEqualTo(YardGrid.ROAD_CELL_COUNT);
		assertThat(slot).isEqualTo(YardGrid.SLOT_COUNT);
	}

	@Test
	@DisplayName("블록 네 개의 경계가 도면과 같다")
	void blockBounds() {
		assertThat(BlockLayout.B01.originRow()).isEqualTo(4);
		assertThat(BlockLayout.B01.originCol()).isEqualTo(4);
		assertThat(BlockLayout.B01.lastRow()).isEqualTo(8);
		assertThat(BlockLayout.B01.lastCol()).isEqualTo(20);

		assertThat(BlockLayout.B04.originRow()).isEqualTo(13);
		assertThat(BlockLayout.B04.originCol()).isEqualTo(25);
		assertThat(BlockLayout.B04.lastRow()).isEqualTo(17);
		assertThat(BlockLayout.B04.lastCol()).isEqualTo(41);

		// 블록 사이 십자 통로와 바깥 테두리는 도로다.
		assertThat(YardGrid.isRoad(9, 10)).isTrue();
		assertThat(YardGrid.isRoad(5, 21)).isTrue();
		assertThat(YardGrid.isRoad(0, 0)).isTrue();
		assertThat(YardGrid.isRoad(21, 45)).isTrue();
	}

	@Test
	@DisplayName("블록 네 면이 모두 도로에 닿아 있다")
	void everyBlockTouchesRoadOnAllFourSides() {
		for (BlockLayout layout : BlockLayout.values()) {
			int midCol = layout.originCol() + YardGrid.BLOCK_COLS / 2;
			int midRow = layout.originRow() + YardGrid.BLOCK_ROWS / 2;
			assertThat(YardGrid.isRoad(layout.originRow() - 1, midCol)).isTrue();
			assertThat(YardGrid.isRoad(layout.lastRow() + 1, midCol)).isTrue();
			assertThat(YardGrid.isRoad(midRow, layout.originCol() - 1)).isTrue();
			assertThat(YardGrid.isRoad(midRow, layout.lastCol() + 1)).isTrue();
		}
	}

	@Test
	@DisplayName("depth 는 진입 도로에서 0~10 이고, 레인 양끝이 도로에 붙은 0 이다")
	void depthIsMeasuredFromNearestRoad() {
		BlockLayout b = BlockLayout.B01;

		assertThat(b.depth(b.originRow())).isZero();
		assertThat(b.accessSide(b.originRow())).isEqualTo(AccessSide.NORTH);

		assertThat(b.depth(b.lastRow())).isZero();
		assertThat(b.accessSide(b.lastRow())).isEqualTo(AccessSide.SOUTH);

		// 가운데 두 칸이 가장 깊다.
		assertThat(b.depth(b.originRow() + 2)).isEqualTo(2);
		assertThat(b.accessSide(b.originRow() + 2)).isEqualTo(AccessSide.NORTH);
		assertThat(b.depth(b.originRow() + 3)).isEqualTo(1);
		assertThat(b.accessSide(b.originRow() + 3)).isEqualTo(AccessSide.SOUTH);

		for (int row = b.originRow(); row <= b.lastRow(); row++) {
			assertThat(b.depth(row)).isBetween(0, YardGrid.DEPTH_PER_LANE - 1);
		}
	}

	@Test
	@DisplayName("(블록, 레인, 진입방향, depth) 조합이 슬롯마다 유일하다")
	void lanePositionIsUnique() {
		Set<String> keys = new HashSet<>();
		for (YardGrid.Cell cell : YardGrid.slotCells()) {
			BlockLayout layout = YardGrid.blockAt(cell.row(), cell.col()).orElseThrow();
			keys.add("%s-%d-%s-%d".formatted(
					layout.blockId(), layout.lane(cell.col()),
					layout.accessSide(cell.row()), layout.depth(cell.row())));
		}
		assertThat(keys).hasSize(YardGrid.SLOT_COUNT);
	}

	@Test
	@DisplayName("슬롯 ID 는 유일하고 좌표로 되돌릴 수 있다")
	void slotIdRoundTrip() {
		List<YardGrid.Cell> cells = YardGrid.slotCells();
		Set<String> ids = new HashSet<>();
		for (YardGrid.Cell cell : cells) {
			String id = YardGrid.slotId(cell.row(), cell.col());
			ids.add(id);
			assertThat(YardGrid.cellOf(id)).isEqualTo(cell);
		}
		assertThat(ids).hasSize(YardGrid.SLOT_COUNT);
		assertThat(YardGrid.slotId(4, 7)).isEqualTo("B01-R04-C07");
		assertThat(YardGrid.slotId(17, 41)).isEqualTo("B04-R17-C41");
	}

	@Test
	@DisplayName("도로 칸에는 슬롯 ID 가 없다")
	void roadCellHasNoSlotId() {
		assertThatThrownBy(() -> YardGrid.slotId(9, 22))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> YardGrid.cellOf("B01-R09-C22"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("슬롯의 진입 도로 칸은 레인 바로 바깥의 도로다")
	void accessRoadCellIsAdjacentRoad() {
		Block block = Block.from(BlockLayout.B03);

		Slot north = Slot.at(block, 13, 12);
		assertThat(north.getAccessSide()).isEqualTo(AccessSide.NORTH);
		assertThat(north.getDepth()).isZero();
		assertThat(north.accessRoadCell()).isEqualTo(new YardGrid.Cell(12, 12));
		assertThat(YardGrid.isRoad(12, 12)).isTrue();

		Slot south = Slot.at(block, 17, 12);
		assertThat(south.getAccessSide()).isEqualTo(AccessSide.SOUTH);
		assertThat(south.accessRoadCell()).isEqualTo(new YardGrid.Cell(18, 12));
		assertThat(YardGrid.isRoad(18, 12)).isTrue();
	}

	@Test
	@DisplayName("블록 밖 좌표로 슬롯을 만들 수 없다")
	void slotOutsideBlockRejected() {
		Block block = Block.from(BlockLayout.B01);
		assertThatThrownBy(() -> Slot.at(block, 9, 10))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
