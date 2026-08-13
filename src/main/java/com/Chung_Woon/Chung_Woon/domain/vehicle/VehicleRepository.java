package com.Chung_Woon.Chung_Woon.domain.vehicle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, String> {

	/**
	 * 지금까지 발급된 가장 큰 차량 ID. 없으면 비어 있다.
	 *
	 * <p>새 ID 를 만들 때 {@code count()} 를 쓰면 안 된다 — 차가 한 대라도 삭제되면 이미 쓰인 번호를
	 * 다시 내주고, PK 가 수동 할당이라 {@code saveAll} 이 merge(UPSERT)로 동작해서 기존 행을
	 * 예외 없이 덮어쓴다. ID 는 문자열이지만 자리수가 고정(V-0001)이라 문자열 정렬 = 숫자 정렬이다.
	 */
	@Query("select max(v.vehicleId) from Vehicle v")
	Optional<String> findMaxVehicleId();

	Optional<Vehicle> findByVin(String vin);

	List<Vehicle> findByBrand(String brand);

	List<Vehicle> findByNextMode(NextMode nextMode);

	List<Vehicle> findByStatus(VehicleStatus status);

	long countByStatus(VehicleStatus status);

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
