package com.Chung_Woon.Chung_Woon.domain.vehicle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 차량 ID 발급의 근거가 되는 조회. 여기가 깨지면 선하증권 전개가 남의 차량 행을 덮어쓴다.
 *
 * <p>{@code count()} 로 다음 번호를 정하던 시절의 회귀를 막는다 — 삭제가 한 번이라도 있으면
 * count 는 줄어들지만 이미 발급된 번호는 그대로라, 같은 ID 를 다시 내주고 PK 가 수동 할당이라
 * {@code saveAll} 이 merge(UPSERT)로 돌아 <b>예외 없이</b> 기존 행을 덮어쓴다.
 */
@DataJpaTest
class VehicleRepositoryTest {

	@Autowired
	private VehicleRepository vehicleRepository;

	@Test
	@DisplayName("차량이 없으면 최대 ID 도 없다")
	void emptyWhenNoVehicle() {
		assertThat(vehicleRepository.findMaxVehicleId()).isEmpty();
	}

	@Test
	@DisplayName("중간 차량이 삭제돼도 최대 ID 는 줄어들지 않는다")
	void maxIdSurvivesDeletion() {
		vehicleRepository.saveAll(java.util.List.of(
				vehicle("V-0001"), vehicle("V-0002"), vehicle("V-0003")));
		vehicleRepository.flush();

		vehicleRepository.deleteById("V-0002");
		vehicleRepository.flush();

		// count() 는 2 로 줄어서 다음 번호를 V-0003 으로 잘못 계산했었다.
		assertThat(vehicleRepository.count()).isEqualTo(2);
		assertThat(vehicleRepository.findMaxVehicleId()).contains("V-0003");
	}

	@Test
	@DisplayName("자리수가 고정이라 문자열 정렬이 곧 번호 순이다")
	void stringOrderMatchesNumericOrder() {
		vehicleRepository.saveAll(java.util.List.of(
				vehicle("V-0009"), vehicle("V-0010"), vehicle("V-0002")));
		vehicleRepository.flush();

		assertThat(vehicleRepository.findMaxVehicleId()).contains("V-0010");
	}

	private Vehicle vehicle(String vehicleId) {
		return Vehicle.builder()
				.vehicleId(vehicleId)
				.vin("VIN-" + vehicleId)
				.build();
	}
}
