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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 차량 한 대가 서는 자리. PK 는 "B03-L02-D04" (블록-레인-깊이).
 *
 * <p>depth 가 클수록 통로에서 멀다. 깊은 자리에 먼저 나갈 차를 넣으면 앞차를 빼야 하므로(재취급),
 * 재취급 Proxy 지표는 이 depth 를 기준으로 계산한다.
 */
@Entity
@Table(name = "slot")
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

	@Column(nullable = false)
	private int lane;

	/** 통로에서 몇 번째 깊이인지. 0 이 통로에 가장 가깝다. */
	@Column(nullable = false)
	private int depth;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SlotStatus status;

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
}
