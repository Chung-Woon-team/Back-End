package com.Chung_Woon.Chung_Woon.domain.vehicle;

import com.Chung_Woon.Chung_Woon.domain.billoflading.BillOfLading;
import com.Chung_Woon.Chung_Woon.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 완성차 한 대.
 *
 * <p>필드가 세 갈래에서 온다.
 * <ul>
 *   <li><b>선하증권</b> — VIN, brand/model, destination, 하선 순번, 동력원, 자력주행 여부</li>
 *   <li><b>야드 운영</b> — 입고/출고 시각, 현재 상태</li>
 *   <li><b>후속 운송 계획</b> — nextMode, 출고 컷오프, 긴급도 (선하증권에 없어 별도 확보)</li>
 * </ul>
 */
@Entity
@Table(name = "vehicle", indexes = {
		@Index(name = "idx_vehicle_status", columnList = "status"),
		@Index(name = "idx_vehicle_cutoff", columnList = "departureCutoffAt"),
		@Index(name = "idx_vehicle_brand", columnList = "brand"),
		@Index(name = "idx_vehicle_bl", columnList = "bl_number")
})
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Vehicle extends BaseTimeEntity {

	/** 내부 식별자. "V-0001" 형식 (AI활용방안 8절 ID 규칙) */
	@Id
	@Column(name = "vehicle_id", length = 20)
	private String vehicleId;

	/**
	 * 차대번호. 예: "SYNT26E0000000001"
	 *
	 * <p>선하증권에는 보통 범위("...0001 TO ...0060")로만 적혀 있어서, 개별 VIN 은
	 * 코드가 전개해 채운다. 실물과 대조할 수 있는 유일한 값이라 유니크로 잡았다.
	 */
	@Column(unique = true, length = 30)
	private String vin;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "bl_number")
	private BillOfLading billOfLading;

	// --- 선하증권에서 ---

	@Column(length = 40)
	private String brand;

	@Column(length = 80)
	private String model;

	/** 최종 목적지 코드. 예: "DEST-DE" */
	@Column(length = 40)
	private String destination;

	/** 선박에서 내리는 순서. 작을수록 먼저 내린다. 선하증권의 SEQ 구간 안 개별 번호. */
	private Integer dischargeSequence;

	@Enumerated(EnumType.STRING)
	@Column(length = 10)
	private UnloadingPriority unloadingPriority;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private Powertrain powertrain;

	/**
	 * 자력주행 가능 여부. false 면 견인이 필요하고, 이동 비용과 작업 순서가 완전히 달라진다.
	 * 선하증권 샘플의 "40 DRIVEABLE / 2 TOW" 가 이 필드로 풀린다.
	 */
	@Column(nullable = false)
	@Builder.Default
	private boolean driveable = true;

	/** 배터리 SOC 또는 연료 잔량(%). 낮으면 먼 슬롯까지 자력 이동을 못 시킨다. */
	private Integer energyLevelPct;

	/** 전고(m). 3.8m 같은 고데크 차량은 오버사이즈 레인에만 들어간다. */
	private Double heightMeters;

	// --- 야드 운영 ---

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	@Builder.Default
	private VehicleStatus status = VehicleStatus.EXPECTED;

	/** 실제 야드 입고 시각 (하선 완료). */
	private LocalDateTime arrivedAt;

	/** 실제 야드 출고 시각. 채워지면 배치 계산 대상에서 빠진다. */
	private LocalDateTime departedAt;

	// --- 후속 운송 계획 (선하증권에 없음) ---

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private NextMode nextMode;

	/** 출고 컷오프. 임박할수록 게이트 가까운 얕은 슬롯에 둬야 한다. */
	private LocalDateTime departureCutoffAt;

	/** 출고 긴급도. 하선 순서(unloadingPriority)와는 다른 축이다. */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	@Builder.Default
	private VehiclePriority priority = VehiclePriority.NORMAL;

	// --- 상태 전이 ---

	/** 하선 완료 → 야드 주차중. */
	public void arriveAt(LocalDateTime when) {
		this.status = VehicleStatus.IN_YARD;
		this.arrivedAt = when;
	}

	/** 야드 출고. 이후 배치 계산에서 제외된다. */
	public void departAt(LocalDateTime when) {
		this.status = VehicleStatus.DEPARTED;
		this.departedAt = when;
	}

	public void assignOutboundPlan(NextMode nextMode, LocalDateTime departureCutoffAt) {
		this.nextMode = nextMode;
		this.departureCutoffAt = departureCutoffAt;
	}

	public void markUrgent() {
		this.priority = VehiclePriority.URGENT;
	}

	/** 배치 계산 대상인지. 이미 나간 차를 옮기라는 지시가 나가지 않게 막는다. */
	public boolean isPlannable() {
		return this.status != VehicleStatus.DEPARTED;
	}

	/** 야드에 머문 시간(분). 체류 시간 KPI 용. 아직 안 나갔으면 null. */
	public Long dwellMinutes() {
		if (this.arrivedAt == null || this.departedAt == null) {
			return null;
		}
		return java.time.Duration.between(this.arrivedAt, this.departedAt).toMinutes();
	}
}
