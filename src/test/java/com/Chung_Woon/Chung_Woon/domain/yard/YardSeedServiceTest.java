package com.Chung_Woon.Chung_Woon.domain.yard;

import com.Chung_Woon.Chung_Woon.api.dto.YardSeedRequest;
import com.Chung_Woon.Chung_Woon.domain.vehicle.VehicleRepository;
import com.Chung_Woon.Chung_Woon.domain.vehicle.VehicleStatus;
import com.Chung_Woon.Chung_Woon.global.error.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 사진에서 읽은 점유 상태가 그대로 들어가는지. */
@SpringBootTest
@TestPropertySource(properties = "demo.seed-api.enabled=true")
@Transactional
class YardSeedServiceTest {

	@Autowired
	private YardSeedService yardSeedService;

	@Autowired
	private SlotRepository slotRepository;

	@Autowired
	private VehicleRepository vehicleRepository;

	@Autowired
	private BlockRepository blockRepository;

	private YardSeedRequest request(List<YardSeedRequest.BlockOccupancy> blocks,
			List<YardSeedRequest.ClosedBlock> closed) {
		return new YardSeedRequest(blocks, closed, true, null, null);
	}

	@Test
	@DisplayName("블록별 대수만큼 차량이 생기고 슬롯이 점유된다")
	void fillsRequestedCounts() {
		YardSeedRequest.Result result = yardSeedService.seed(request(
				List.of(new YardSeedRequest.BlockOccupancy("B01", 10, null),
						new YardSeedRequest.BlockOccupancy("B03", 5, null)),
				List.of()));

		assertThat(result.vehiclesCreated()).isEqualTo(15);
		assertThat(result.slotsOccupied()).isEqualTo(15);
		assertThat(result.slotsAvailable()).isEqualTo(YardGrid.SLOT_COUNT - 15);

		assertThat(vehicleRepository.countByStatus(VehicleStatus.IN_YARD)).isEqualTo(15);
		assertThat(slotRepository.findByBlock_BlockId("B01").stream()
				.filter(s -> s.getStatus() == SlotStatus.OCCUPIED).count()).isEqualTo(10);
	}

	@Test
	@DisplayName("통로에 가까운 자리(depth 0)부터 채운다")
	void fillsShallowSlotsFirst() {
		yardSeedService.seed(request(
				List.of(new YardSeedRequest.BlockOccupancy("B01", 5, null)), List.of()));

		assertThat(slotRepository.findByBlock_BlockId("B01").stream()
				.filter(s -> s.getStatus() == SlotStatus.OCCUPIED)
				.allMatch(s -> s.getDepth() == 0)).isTrue();
	}

	@Test
	@DisplayName("폐쇄된 블록은 가용 슬롯에서 통째로 빠진다")
	void closedBlockRemovesItsSlotsFromAvailable() {
		YardSeedRequest.Result result = yardSeedService.seed(request(
				List.of(),
				List.of(new YardSeedRequest.ClosedBlock("B02", "도색작업"))));

		assertThat(result.closedBlocks()).containsExactly("B02");
		assertThat(blockRepository.findById("B02").orElseThrow().isClosed()).isTrue();
		// 비어 있어도 닫힌 블록의 484칸은 배치 대상이 아니다.
		assertThat(result.slotsAvailable())
				.isEqualTo(YardGrid.SLOT_COUNT - YardGrid.SLOTS_PER_BLOCK);
	}

	@Test
	@DisplayName("블록 용량(484)을 넘기면 거부한다")
	void rejectsOverCapacity() {
		assertThatThrownBy(() -> yardSeedService.seed(request(
				List.of(new YardSeedRequest.BlockOccupancy("B01", 485, null)), List.of())))
				.isInstanceOf(BusinessException.class);
	}

	@Test
	@DisplayName("없는 블록이면 거부한다")
	void rejectsUnknownBlock() {
		assertThatThrownBy(() -> yardSeedService.seed(request(
				List.of(new YardSeedRequest.BlockOccupancy("B99", 1, null)), List.of())))
				.isInstanceOf(BusinessException.class);
	}

	@Test
	@DisplayName("reset 은 이전 적재를 지우고 다시 넣는다 — 두 번 돌려도 두 배가 되지 않는다")
	void resetIsIdempotent() {
		yardSeedService.seed(request(
				List.of(new YardSeedRequest.BlockOccupancy("B01", 10, null)), List.of()));
		YardSeedRequest.Result second = yardSeedService.seed(request(
				List.of(new YardSeedRequest.BlockOccupancy("B01", 10, null)), List.of()));

		assertThat(second.vehiclesRemoved()).isEqualTo(10);
		assertThat(second.vehiclesCreated()).isEqualTo(10);
		assertThat(vehicleRepository.count()).isEqualTo(10);
	}

	@Test
	@DisplayName("전체 삭제는 차량을 지우고 폐쇄도 풀어 빈 야드로 되돌린다")
	void clearAllResetsEverything() {
		yardSeedService.seed(request(
				List.of(new YardSeedRequest.BlockOccupancy("B01", 20, null)),
				List.of(new YardSeedRequest.ClosedBlock("B02", "도색작업"))));

		YardSeedRequest.ClearResult result = yardSeedService.clearAll();

		assertThat(result.vehiclesRemoved()).isEqualTo(20);
		assertThat(result.slotsAvailable()).isEqualTo(YardGrid.SLOT_COUNT);
		assertThat(vehicleRepository.count()).isZero();
		assertThat(slotRepository.countByStatus(SlotStatus.OCCUPIED)).isZero();
		assertThat(blockRepository.findById("B02").orElseThrow().isClosed()).isFalse();
	}

	@Test
	@DisplayName("빈 야드에서 전체 삭제해도 안전하다")
	void clearAllOnEmptyYard() {
		YardSeedRequest.ClearResult result = yardSeedService.clearAll();

		assertThat(result.vehiclesRemoved()).isZero();
		assertThat(result.slotsAvailable()).isEqualTo(YardGrid.SLOT_COUNT);
	}
}
