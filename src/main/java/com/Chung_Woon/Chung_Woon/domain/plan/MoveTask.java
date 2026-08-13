package com.Chung_Woon.Chung_Woon.domain.plan;

import com.Chung_Woon.Chung_Woon.domain.vehicle.Vehicle;
import com.Chung_Woon.Chung_Woon.domain.yard.Slot;
import com.Chung_Woon.Chung_Woon.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
 * 현장에 실제로 나가는 작업지시 한 줄 — "V-0182 를 B03-L02-D04 에서 B05-L01-D00 으로 옮겨라".
 *
 * <p>{@code reason} 을 반드시 채운다. 장표 7쪽 Minimal Replanning 의 "변경 이유 기록"이 이것이고,
 * 현장 입장에서 이유 없는 이동 지시는 신뢰를 잃는다.
 */
@Entity
@Table(name = "move_task")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MoveTask extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "plan_revision_id", nullable = false)
	private PlanRevision planRevision;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "vehicle_id", nullable = false)
	private Vehicle vehicle;

	/** 이전 판에서의 자리. 신규 입고 차량이면 null. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "from_slot_id")
	private Slot fromSlot;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "to_slot_id", nullable = false)
	private Slot toSlot;

	/** 작업 순서. 앞차를 먼저 빼야 하는 경우가 있어 순서가 의미를 가진다. */
	@Column(nullable = false)
	private int sequence;

	/** 왜 옮기는지. 예: "B03 폐쇄(C-001)" */
	@Column(nullable = false, length = 200)
	private String reason;

	/** 이동 거리(m). KPI 집계의 원천값. */
	private Double distanceMeters;
}
