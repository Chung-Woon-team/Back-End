package com.Chung_Woon.Chung_Woon.domain.plan;

import com.Chung_Woon.Chung_Woon.domain.vehicle.Vehicle;
import com.Chung_Woon.Chung_Woon.domain.yard.Slot;
import com.Chung_Woon.Chung_Woon.global.common.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
 * "이 판에서 이 차는 이 슬롯" 한 줄. 판마다 전체 배치가 통째로 남는다.
 *
 * <p>같은 판 안에서 한 슬롯에 두 대가 들어갈 수 없도록 DB 제약을 건다.
 * 최적화 코드에 버그가 있어도 여기서 걸린다.
 */
@Entity
@Table(name = "placement", uniqueConstraints = {
		@UniqueConstraint(name = "uk_placement_plan_slot", columnNames = {"plan_revision_id", "slot_id"}),
		@UniqueConstraint(name = "uk_placement_plan_vehicle", columnNames = {"plan_revision_id", "vehicle_id"})
})
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Placement extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "plan_revision_id", nullable = false)
	private PlanRevision planRevision;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "vehicle_id", nullable = false)
	private Vehicle vehicle;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "slot_id", nullable = false)
	private Slot slot;
}
