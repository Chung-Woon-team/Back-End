package com.Chung_Woon.Chung_Woon.domain.yard;

import com.Chung_Woon.Chung_Woon.api.dto.YardOccupancyResponse;
import com.Chung_Woon.Chung_Woon.domain.vehicle.Vehicle;
import com.Chung_Woon.Chung_Woon.domain.vehicle.VehicleRepository;
import com.Chung_Woon.Chung_Woon.domain.vehicle.VehicleStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 야드 전체 상태를 만든다. 배치·경로 알고리즘의 입력이자 야드 화면의 원본이다.
 *
 * <p><b>격자 구조는 코드가 정본이다.</b> 어느 칸이 주차칸이고 레인·depth 가 얼마인지는
 * {@link YardGrid}·{@link BlockLayout} 이 알고 있으므로, DB 적재 여부와 무관하게 340칸을 전부 만든다.
 * DB 에서 오는 건 <b>상태</b>뿐이다 — 누가 서 있고 어느 블록이 닫혔는지.
 *
 * <p>이렇게 나눈 이유: 예전에는 블록·슬롯을 DB 에서만 읽어서, 테이블이 비어 있으면
 * {@code blocks: []} / {@code occupied: []} 만 나가 화면이 아무것도 못 그렸다.
 * 구조는 항상 알 수 있는 값이라 DB 적재를 기다릴 이유가 없다.
 */
@Service
@RequiredArgsConstructor
public class YardOccupancyService {

	private final BlockRepository blockRepository;
	private final SlotRepository slotRepository;
	private final VehicleRepository vehicleRepository;

	@Transactional(readOnly = true)
	public YardOccupancyResponse snapshot() {
		Map<String, Block> blocksById = blocksById();
		Map<String, Slot> slotsById = slotsById();
		Map<String, Vehicle> vehiclesBySlotId = vehiclesBySlotId();

		boolean gridSeeded = slotsById.size() == YardGrid.SLOT_COUNT;

		List<YardOccupancyResponse.SlotState> slots = buildSlots(slotsById, vehiclesBySlotId);
		Map<String, Long> occupiedByBlock = countOccupiedByBlock(slots);

		return new YardOccupancyResponse(
				new YardOccupancyResponse.Grid(
						YardGrid.ROW_COUNT, YardGrid.COL_COUNT, YardGrid.ROAD_WIDTH,
						YardGrid.BLOCK_ROWS, YardGrid.BLOCK_COLS,
						YardGrid.SLOT_COUNT, YardGrid.ROAD_CELL_COUNT),
				gridSeeded,
				buildSummary(slots, blocksById),
				buildBlocks(blocksById, occupiedByBlock),
				slots,
				LocalDateTime.now());
	}

	// ------------------------------------------------------------------ 조회

	private Map<String, Block> blocksById() {
		Map<String, Block> map = new HashMap<>();
		blockRepository.findAll().forEach(b -> map.put(b.getBlockId(), b));
		return map;
	}

	private Map<String, Slot> slotsById() {
		Map<String, Slot> map = new HashMap<>();
		slotRepository.findAll().forEach(s -> map.put(s.getSlotId(), s));
		return map;
	}

	/** 슬롯 ID → 거기 서 있는 차. 자리를 모르는 차는 빠진다. */
	private Map<String, Vehicle> vehiclesBySlotId() {
		Map<String, Vehicle> map = new HashMap<>();
		for (Vehicle v : vehicleRepository.findByStatus(VehicleStatus.IN_YARD)) {
			if (v.getCurrentSlot() != null) {
				map.put(v.getCurrentSlot().getSlotId(), v);
			}
		}
		return map;
	}

	// ------------------------------------------------------------------ 조립

	/** 340칸 전부. 구조는 코드에서, 상태는 DB 에서. */
	private List<YardOccupancyResponse.SlotState> buildSlots(
			Map<String, Slot> slotsById, Map<String, Vehicle> vehiclesBySlotId) {

		List<YardOccupancyResponse.SlotState> result = new ArrayList<>(YardGrid.SLOT_COUNT);
		for (BlockLayout layout : BlockLayout.values()) {
			for (int row = layout.originRow(); row <= layout.lastRow(); row++) {
				for (int col = layout.originCol(); col <= layout.lastCol(); col++) {
					String slotId = YardGrid.slotId(row, col);
					Slot stored = slotsById.get(slotId);
					Vehicle vehicle = vehiclesBySlotId.get(slotId);

					result.add(new YardOccupancyResponse.SlotState(
							slotId,
							layout.blockId(),
							row,
							col,
							layout.lane(col),
							layout.depth(row),
							layout.accessSide(row).name(),
							// DB 행이 없으면 아직 적재 전이다. grid_seeded 가 false 로 나간다.
							stored != null ? stored.getStatus().name() : SlotStatus.EMPTY.name(),
							vehicle != null ? vehicle.getVehicleId() : null,
							vehicle != null && vehicle.getNextMode() != null
									? vehicle.getNextMode().name() : null,
							vehicle != null ? vehicle.getDepartureCutoffAt() : null));
				}
			}
		}
		return result;
	}

	private Map<String, Long> countOccupiedByBlock(List<YardOccupancyResponse.SlotState> slots) {
		Map<String, Long> counts = new HashMap<>();
		for (YardOccupancyResponse.SlotState s : slots) {
			if (SlotStatus.OCCUPIED.name().equals(s.status())) {
				counts.merge(s.blockId(), 1L, Long::sum);
			}
		}
		return counts;
	}

	private YardOccupancyResponse.Summary buildSummary(
			List<YardOccupancyResponse.SlotState> slots, Map<String, Block> blocksById) {

		long occupied = slots.stream()
				.filter(s -> SlotStatus.OCCUPIED.name().equals(s.status()))
				.count();
		// 닫힌 블록의 빈 칸은 배치 대상이 아니다.
		long available = slots.stream()
				.filter(s -> SlotStatus.EMPTY.name().equals(s.status()))
				.filter(s -> !isClosed(blocksById, s.blockId()))
				.count();

		double pct = slots.isEmpty() ? 0.0 : Math.round(occupied * 1000.0 / slots.size()) / 10.0;
		return new YardOccupancyResponse.Summary(slots.size(), occupied, available, pct);
	}

	private List<YardOccupancyResponse.BlockState> buildBlocks(
			Map<String, Block> blocksById, Map<String, Long> occupiedByBlock) {

		List<YardOccupancyResponse.BlockState> result = new ArrayList<>(YardGrid.BLOCK_COUNT);
		for (BlockLayout layout : BlockLayout.values()) {
			Block stored = blocksById.get(layout.blockId());
			result.add(new YardOccupancyResponse.BlockState(
					layout.blockId(),
					zoneIdOf(layout.blockId()),
					stored != null ? stored.getZoneCode() : layout.zoneCode(),
					layout.originRow(),
					layout.originCol(),
					YardGrid.BLOCK_ROWS,
					YardGrid.BLOCK_COLS,
					YardGrid.SLOTS_PER_BLOCK,
					occupiedByBlock.getOrDefault(layout.blockId(), 0L),
					stored != null && stored.isClosed(),
					stored != null ? stored.getClosureReason() : null));
		}
		return result;
	}

	private boolean isClosed(Map<String, Block> blocksById, String blockId) {
		Block block = blocksById.get(blockId);
		return block != null && block.isClosed();
	}

	/**
	 * 알고리즘 쪽 zone 번호. 파이썬이 {@code B01} 같은 문자열 대신 1~4 정수를 쓴다.
	 * {@code B01 → 1} 처럼 뒤 두 자리를 그대로 쓴다.
	 */
	private int zoneIdOf(String blockId) {
		return Integer.parseInt(blockId.substring(1));
	}
}
