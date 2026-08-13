package com.Chung_Woon.Chung_Woon.domain.yard;

import com.Chung_Woon.Chung_Woon.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 야드의 블록. 슬롯을 담는 단위이며, 도색작업 등으로 통째로 폐쇄될 수 있다.
 * PK 는 현장 ID 규칙 그대로 "B03".
 *
 * <p>도면상 위치(원점 좌표와 크기)는 {@link BlockLayout} 이 정본이고, 이 엔티티는 그 값을 복사해서
 * 들고 있는다. 조회 한 번으로 격자를 그릴 수 있게 하려는 것이다.
 */
@Entity
@Table(name = "block")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Block extends BaseTimeEntity {

	@Id
	@Column(name = "block_id", length = 20)
	private String blockId;

	/**
	 * 구역 코드. 선하증권의 TARGET YARD ZONE 이 이걸 가리킨다.
	 * 예: "EV-A"(전기차), "QC-HOLD"(검사 대기), "HVY-D"(대형)
	 */
	@Column(length = 30)
	private String zoneCode;

	/** 블록 왼쪽 위 칸의 야드 절대 행. 도면 기준 4 또는 30. */
	@Column(name = "origin_row", nullable = false)
	private int originRow;

	/** 블록 왼쪽 위 칸의 야드 절대 열. 도면 기준 4 또는 30. */
	@Column(name = "origin_col", nullable = false)
	private int originCol;

	/** 블록의 행 수(깊이). 사진 기준 5. */
	@Column(nullable = false)
	private int blockRows;

	/** 블록의 열 수(폭). 사진 기준 17. */
	@Column(nullable = false)
	private int blockCols;

	/** 세로 열 개수 = blockCols. 사진 기준 17. */
	@Column(nullable = false)
	private int laneCount;

	/** 한 레인이 한쪽 도로에서 갖는 깊이. 사진 기준 3 (depth 0~2). */
	@Column(nullable = false)
	private int depthPerLane;

	/**
	 * 이 블록에 들어갈 수 있는 최대 전고(m). null 이면 제한 없음.
	 * 3.80m 전기트럭 같은 고데크 화물을 일반 블록에 넣지 않으려면 필요하다.
	 */
	private Double maxHeightMeters;

	/** BLOCK_CLOSURE 제약이 승인되면 true 가 된다. 폐쇄된 블록에는 배치할 수 없다. */
	@Column(nullable = false)
	private boolean closed;

	/** 폐쇄 사유 (예: "도색작업"). 승인된 지시의 원문에서 옮겨 적는다. */
	private String closureReason;

	/** 도면 정의에서 블록 한 개를 만든다. 슬롯은 {@code Slot.at(...)} 이 따로 붙인다. */
	public static Block from(BlockLayout layout) {
		return Block.builder()
				.blockId(layout.blockId())
				.zoneCode(layout.zoneCode())
				.originRow(layout.originRow())
				.originCol(layout.originCol())
				.blockRows(YardGrid.BLOCK_ROWS)
				.blockCols(YardGrid.BLOCK_COLS)
				.laneCount(YardGrid.LANES_PER_BLOCK)
				.depthPerLane(YardGrid.DEPTH_PER_LANE)
				.closed(false)
				.build();
	}

	public void close(String reason) {
		this.closed = true;
		this.closureReason = reason;
	}

	public void reopen() {
		this.closed = false;
		this.closureReason = null;
	}
}
