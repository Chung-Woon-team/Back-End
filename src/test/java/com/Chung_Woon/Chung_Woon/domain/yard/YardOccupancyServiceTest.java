package com.Chung_Woon.Chung_Woon.domain.yard;

import com.Chung_Woon.Chung_Woon.api.dto.YardOccupancyResponse;
import com.Chung_Woon.Chung_Woon.api.dto.YardSeedRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 알고리즘이 "이미 주차된 차" 를 피해가려면 이 스냅샷에 좌표와 출차 시각이 다 들어 있어야 한다.
 */
@SpringBootTest
@TestPropertySource(properties = "demo.seed-api.enabled=true")
@Transactional
class YardOccupancyServiceTest {

	@Autowired
	private YardOccupancyService yardOccupancyService;

	@Autowired
	private YardSeedService yardSeedService;

	@Test
	@DisplayName("빈 야드면 격자 정보만 나오고 점유는 비어 있다")
	void emptyYard() {
		YardOccupancyResponse snapshot = yardOccupancyService.snapshot();

		assertThat(snapshot.grid().rows()).isEqualTo(YardGrid.ROW_COUNT);
		assertThat(snapshot.grid().cols()).isEqualTo(YardGrid.COL_COUNT);
		assertThat(snapshot.grid().totalSlots()).isEqualTo(YardGrid.SLOT_COUNT);
		assertThat(snapshot.blocks()).hasSize(YardGrid.BLOCK_COUNT);
		assertThat(snapshot.occupied()).isEmpty();
	}

	@Test
	@DisplayName("블록이 알고리즘의 zone 번호(1~4)와 원점 좌표를 들고 나온다")
	void blocksCarryZoneAndOrigin() {
		List<YardOccupancyResponse.BlockState> blocks = yardOccupancyService.snapshot().blocks();

		YardOccupancyResponse.BlockState b01 = blocks.get(0);
		assertThat(b01.blockId()).isEqualTo("B01");
		assertThat(b01.zoneId()).isEqualTo(1);
		assertThat(b01.originRow()).isEqualTo(4);
		assertThat(b01.originCol()).isEqualTo(4);
		assertThat(b01.blockRows()).isEqualTo(YardGrid.BLOCK_ROWS);
		assertThat(b01.blockCols()).isEqualTo(YardGrid.BLOCK_COLS);

		// B02 는 우상단 — 알고리즘의 zone 2 와 같아야 한다.
		YardOccupancyResponse.BlockState b02 = blocks.get(1);
		assertThat(b02.zoneId()).isEqualTo(2);
		assertThat(b02.originRow()).isEqualTo(4);
		assertThat(b02.originCol()).isEqualTo(25);
	}

	@Test
	@DisplayName("주차된 차는 좌표(row·col)와 출차 시각·출구를 갖고 나온다")
	void parkedVehiclesCarryCoordinatesAndExit() {
		yardSeedService.seed(new YardSeedRequest(
				List.of(new YardSeedRequest.BlockOccupancy("B01", 3, null)),
				List.of(), true, null, null));

		List<YardOccupancyResponse.ParkedVehicle> parked = yardOccupancyService.snapshot().occupied();

		assertThat(parked).hasSize(3);
		YardOccupancyResponse.ParkedVehicle first = parked.get(0);

		assertThat(first.slotId()).isEqualTo(YardGrid.slotId(first.row(), first.col()));
		assertThat(first.blockId()).isEqualTo("B01");
		// depth 0 부터 채우므로 B01 의 첫 줄(row 4)에 들어간다.
		assertThat(first.row()).isEqualTo(4);
		assertThat(first.nextMode()).isIn("TRUCK", "RAIL");
		assertThat(first.departureCutoffAt()).isNotNull();
	}

	@Test
	@DisplayName("폐쇄된 블록은 closed=true 로 나온다 — 배치 제외 판단은 알고리즘이 한다")
	void closedBlockIsFlagged() {
		yardSeedService.seed(new YardSeedRequest(
				List.of(), List.of(new YardSeedRequest.ClosedBlock("B02", "도색작업")),
				true, null, null));

		YardOccupancyResponse.BlockState b02 = yardOccupancyService.snapshot().blocks().stream()
				.filter(b -> b.blockId().equals("B02"))
				.findFirst().orElseThrow();

		assertThat(b02.closed()).isTrue();
		assertThat(b02.closureReason()).isEqualTo("도색작업");
	}

	@Test
	@DisplayName("출차 컷오프가 차마다 흩어진다 — 전부 같으면 순서를 정할 수 없다")
	void cutoffTimesAreSpread() {
		yardSeedService.seed(new YardSeedRequest(
				List.of(new YardSeedRequest.BlockOccupancy("B01", 10, null)),
				List.of(), true, 30L, 600L));

		long distinct = yardOccupancyService.snapshot().occupied().stream()
				.map(YardOccupancyResponse.ParkedVehicle::departureCutoffAt)
				.distinct()
				.count();

		assertThat(distinct).isGreaterThan(1);
	}
}
