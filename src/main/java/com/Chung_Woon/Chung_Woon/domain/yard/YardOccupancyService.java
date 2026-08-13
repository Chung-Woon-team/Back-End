package com.Chung_Woon.Chung_Woon.domain.yard;

import com.Chung_Woon.Chung_Woon.api.dto.YardOccupancyResponse;
import com.Chung_Woon.Chung_Woon.domain.vehicle.Vehicle;
import com.Chung_Woon.Chung_Woon.domain.vehicle.VehicleRepository;
import com.Chung_Woon.Chung_Woon.domain.vehicle.VehicleStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * 배치·경로 알고리즘에 넘길 야드 스냅샷을 만든다.
 *
 * <p>알고리즘(파이썬)이 <b>이미 주차된 차를 피해서</b> 자리를 고르고 경로를 그리려면
 * "지금 어느 칸에 누가 있고 언제 나가는지" 가 필요하다. 그게 이 응답이다.
 */
@Service
@RequiredArgsConstructor
public class YardOccupancyService {

	private final BlockRepository blockRepository;
	private final VehicleRepository vehicleRepository;

	@Transactional(readOnly = true)
	public YardOccupancyResponse snapshot() {
		return new YardOccupancyResponse(
				new YardOccupancyResponse.Grid(
						YardGrid.ROW_COUNT, YardGrid.COL_COUNT, YardGrid.ROAD_WIDTH,
						YardGrid.BLOCK_ROWS, YardGrid.BLOCK_COLS, YardGrid.SLOT_COUNT),
				blocks(),
				parkedVehicles(),
				LocalDateTime.now());
	}

	private List<YardOccupancyResponse.BlockState> blocks() {
		return blockRepository.findAll().stream()
				.sorted(Comparator.comparing(Block::getBlockId))
				.map(b -> new YardOccupancyResponse.BlockState(
						b.getBlockId(),
						zoneIdOf(b.getBlockId()),
						b.getOriginRow(),
						b.getOriginCol(),
						b.getBlockRows(),
						b.getBlockCols(),
						b.isClosed(),
						b.getClosureReason()))
				.toList();
	}

	/**
	 * 알고리즘 쪽 zone 번호. 파이썬이 {@code B01} 같은 문자열 대신 1~4 정수를 쓴다.
	 * {@code B01 → 1} 처럼 뒤 두 자리를 그대로 쓴다.
	 */
	private int zoneIdOf(String blockId) {
		return Integer.parseInt(blockId.substring(1));
	}

	/**
	 * 야드에 서 있는 차 전부. 자리를 모르는 차(자리 배정 전)는 뺀다 —
	 * 좌표가 없으면 알고리즘이 피해갈 방법이 없다.
	 */
	private List<YardOccupancyResponse.ParkedVehicle> parkedVehicles() {
		return vehicleRepository.findByStatus(VehicleStatus.IN_YARD).stream()
				.filter(v -> v.getCurrentSlot() != null)
				.sorted(Comparator.comparing(Vehicle::getVehicleId))
				.map(v -> {
					Slot slot = v.getCurrentSlot();
					return new YardOccupancyResponse.ParkedVehicle(
							v.getVehicleId(),
							slot.getSlotId(),
							slot.getBlock().getBlockId(),
							slot.getGridRow(),
							slot.getGridCol(),
							v.getNextMode() != null ? v.getNextMode().name() : null,
							v.getDepartureCutoffAt());
				})
				.toList();
	}
}
