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

	@Column(nullable = false)
	private int laneCount;

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

	public void close(String reason) {
		this.closed = true;
		this.closureReason = reason;
	}

	public void reopen() {
		this.closed = false;
		this.closureReason = null;
	}
}
