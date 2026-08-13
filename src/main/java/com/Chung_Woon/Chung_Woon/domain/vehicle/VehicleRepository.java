package com.Chung_Woon.Chung_Woon.domain.vehicle;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, String> {

	Optional<Vehicle> findByVin(String vin);

	List<Vehicle> findByBrand(String brand);

	List<Vehicle> findByNextMode(NextMode nextMode);

	List<Vehicle> findByStatus(VehicleStatus status);

	List<Vehicle> findByBillOfLading_BlNumber(String blNumber);

	/** 하선 작업 순서대로. */
	List<Vehicle> findByBillOfLading_BlNumberOrderByDischargeSequenceAsc(String blNumber);

	/** 배치 계산 대상 — 이미 나간 차는 뺀다. */
	List<Vehicle> findByStatusNot(VehicleStatus status);

	/** 견인 필요 차량. 하선·이동 작업이 달라서 따로 뽑을 일이 많다. */
	List<Vehicle> findByDriveableFalse();

	/** 컷오프가 임박한 차량. 게이트 가까운 얕은 슬롯 우선 배정 대상. */
	List<Vehicle> findByDepartureCutoffAtBeforeOrderByDepartureCutoffAtAsc(LocalDateTime before);
}
