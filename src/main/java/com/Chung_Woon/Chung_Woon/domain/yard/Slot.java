package com.Chung_Woon.Chung_Woon.domain.yard;

import com.Chung_Woon.Chung_Woon.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 차량 한 대가 서는 자리. PK 는 "B01-R04-C07" (블록-야드 절대 행-절대 열).
 *
 * <p>좌표를 PK 에 넣은 이유는 사진 인식 결과가 {@code (row, col)} 로 오기 때문이다.
 * 좌표 ↔ 슬롯 변환은 {@link YardGrid#slotId(int, int)} 와 {@link YardGrid#cellOf(String)} 한 쌍이면 끝난다.
 *
 * <p>{@code depth} 는 <b>진입 도로에서</b> 몇 번째인지다(0~10). 블록이 네 면 모두 도로에 닿아 있어서
 * 한 레인(세로 22칸)은 위·아래 양쪽에서 들어가고, 어느 쪽인지는 {@code accessSide} 가 들고 있다.
 * 재취급 Proxy 지표는 이 depth 를 쓴다 — depth 가 깊은 자리에 먼저 나갈 차를 넣으면 앞차를 빼야 한다.
 */
@Entity
@Table(
		name = "slot",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_slot_grid", columnNames = {"grid_row", "grid_col"}),
				@UniqueConstraint(
						name = "uk_slot_lane_position",
						columnNames = {"block_id", "lane", "access_side", "depth"})
		})
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Slot extends BaseTimeEntity {

	@Id
	@Column(name = "slot_id", length = 40)
	private String slotId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "block_id", nullable = false)
	private Block block;

	/** 야드 절대 행. 0 ~ 55. */
	@Column(name = "grid_row", nullable = false)
	private int gridRow;

	/** 야드 절대 열. 0 ~ 55. */
	@Column(name = "grid_col", nullable = false)
	private int gridCol;

	/** 블록 안의 세로 열 번호. 0 ~ 21. */
	@Column(nullable = false)
	private int lane;

	/** 진입 도로에서 몇 번째 자리인지. 0 이 도로에 바로 붙어 있다. 0 ~ 10. */
	@Column(nullable = false)
	private int depth;

	/** 이 자리가 레인의 위쪽 도로에서 들어가는지 아래쪽 도로에서 들어가는지. */
	@Enumerated(EnumType.STRING)
	@Column(name = "access_side", nullable = false, length = 10)
	private AccessSide accessSide;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SlotStatus status;

	/** 도면 좌표에서 슬롯을 만든다. lane/depth/accessSide 는 좌표에서 유도되므로 따로 받지 않는다. */
	public static Slot at(Block block, int gridRow, int gridCol) {
		BlockLayout layout = BlockLayout.of(block.getBlockId());
		if (!layout.contains(gridRow, gridCol)) {
			throw new IllegalArgumentException(
					"(%d, %d) 은(는) 블록 %s 밖이다".formatted(gridRow, gridCol, block.getBlockId()));
		}
		return Slot.builder()
				.slotId(YardGrid.slotId(gridRow, gridCol))
				.block(block)
				.gridRow(gridRow)
				.gridCol(gridCol)
				.lane(layout.lane(gridCol))
				.depth(layout.depth(gridRow))
				.accessSide(layout.accessSide(gridRow))
				.status(SlotStatus.EMPTY)
				.build();
	}

	public void markOccupied() {
		this.status = SlotStatus.OCCUPIED;
	}

	public void markEmpty() {
		this.status = SlotStatus.EMPTY;
	}

	public void markBlocked() {
		this.status = SlotStatus.BLOCKED;
	}

	public boolean isAssignable() {
		return this.status == SlotStatus.EMPTY && !this.block.isClosed();
	}

	/** 이 자리로 들어가고 나오는 도로 칸. 경로 알고리즘의 출발/도착 지점이다. */
	public YardGrid.Cell accessRoadCell() {
		BlockLayout layout = BlockLayout.of(this.block.getBlockId());
		int roadRow = this.accessSide == AccessSide.NORTH
				? layout.originRow() - 1
				: layout.lastRow() + 1;
		return new YardGrid.Cell(roadRow, this.gridCol);
	}
}
