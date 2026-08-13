package com.Chung_Woon.Chung_Woon.domain.yard;

import com.Chung_Woon.Chung_Woon.api.dto.YardSeedRequest;
import com.Chung_Woon.Chung_Woon.domain.vehicle.NextMode;
import com.Chung_Woon.Chung_Woon.domain.vehicle.Vehicle;
import com.Chung_Woon.Chung_Woon.domain.vehicle.VehicleRepository;
import com.Chung_Woon.Chung_Woon.domain.vehicle.VehicleStatus;
import com.Chung_Woon.Chung_Woon.global.error.BusinessException;
import com.Chung_Woon.Chung_Woon.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 데모용 야드 점유 상태를 채운다. 실제 야드 사진을 보고 읽은 값을 그대로 넣는 용도다.
 *
 * <p><b>운영 데이터를 지울 수 있는 코드다.</b> {@code demo.seed-api.enabled=true} 일 때만
 * 빈이 만들어지고, 컨트롤러도 같은 조건으로만 열린다. 기본값은 꺼짐이다.
 *
 * <p>차량은 블록 앞쪽(depth 얕은 자리)부터 채운다 — 실제 야드가 그렇게 차고, 화면에서도
 * 통로 쪽부터 차 있는 게 자연스럽다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class YardSeedService {

	private static final String DEMO_VIN_PREFIX = "DEMO";

	private final BlockRepository blockRepository;
	private final SlotRepository slotRepository;
	private final VehicleRepository vehicleRepository;

	@Transactional
	public YardSeedRequest.Result seed(YardSeedRequest request) {
		int removed = request.isReset() ? clear() : 0;

		List<String> closed = closeBlocks(request);
		int created = fillBlocks(request);

		long occupied = slotRepository.countByStatus(SlotStatus.OCCUPIED);
		long available = slotRepository.countAssignable();

		log.info("야드 데모 적재 — 생성 {}대, 삭제 {}대, 점유 {}칸, 가용 {}칸, 폐쇄 {}",
				created, removed, occupied, available, closed);

		return YardSeedRequest.Result.of(created, removed, occupied, available, closed);
	}

	/**
	 * 차량을 전부 지우고 야드를 빈 상태로 되돌린다. 격자는 남는다.
	 * 폐쇄된 블록도 같이 열린다 — "처음 상태" 로 되돌리는 게 목적이다.
	 */
	@Transactional
	public YardSeedRequest.ClearResult clearAll() {
		int removed = clear();
		long available = slotRepository.countAssignable();
		log.info("야드 초기화 — 차량 {}대 삭제, 가용 {}칸", removed, available);
		return new YardSeedRequest.ClearResult(removed, available);
	}

	/**
	 * 차량과 점유를 전부 되돌린다. 격자(블록·슬롯 행) 자체는 남긴다.
	 *
	 * <p>{@code deleteAllInBatch()} 를 쓰면 안 된다 — 벌크 DELETE 는 영속성 컨텍스트를 우회해서,
	 * 같은 트랜잭션에서 같은 ID(V-0001…)로 다시 저장할 때 Hibernate 가 UPDATE 를 시도하고
	 * "expected row count 1 but was 0" 으로 터진다.
	 */
	@Transactional
	public int clear() {
		int removed = (int) vehicleRepository.count();
		// 슬롯 참조를 먼저 끊어야 FK 제약에 걸리지 않는다.
		vehicleRepository.findAll().forEach(Vehicle::leaveSlot);
		vehicleRepository.deleteAll();

		slotRepository.findByStatus(SlotStatus.OCCUPIED).forEach(Slot::markEmpty);
		blockRepository.findAll().forEach(Block::reopen);
		return removed;
	}

	private List<String> closeBlocks(YardSeedRequest request) {
		List<String> closed = new ArrayList<>();
		for (YardSeedRequest.ClosedBlock entry : request.closedBlocksOrEmpty()) {
			Block block = block(entry.blockId());
			block.close(entry.reason() != null ? entry.reason() : "현장 이벤트");
			closed.add(block.getBlockId());
		}
		return closed;
	}

	private int fillBlocks(YardSeedRequest request) {
		long nextNumber = nextVehicleNumber();
		int created = 0;

		for (YardSeedRequest.BlockOccupancy entry : request.blocksOrEmpty()) {
			Block block = block(entry.blockId());
			int capacity = block.getBlockRows() * block.getBlockCols();
			if (entry.occupied() < 0 || entry.occupied() > capacity) {
				throw new BusinessException(ErrorCode.INVALID_INPUT,
						"%s 의 occupied 는 0 ~ %d 여야 합니다. 받은 값: %d"
								.formatted(block.getBlockId(), capacity, entry.occupied()));
			}

			// 빈 자리만, 통로에 가까운 순서로 채운다.
			List<Slot> targets = slotRepository.findByBlock_BlockId(block.getBlockId()).stream()
					.filter(s -> s.getStatus() == SlotStatus.EMPTY)
					.sorted(Comparator.comparingInt(Slot::getDepth).thenComparingInt(Slot::getLane))
					.limit(entry.occupied())
					.toList();

			List<String> brands = entry.brandsOrDefault();
			List<Vehicle> vehicles = new ArrayList<>(targets.size());
			for (Slot slot : targets) {
				long number = nextNumber++;
				Vehicle vehicle = Vehicle.builder()
						.vehicleId("V-%04d".formatted(number))
						.vin("%s%013d".formatted(DEMO_VIN_PREFIX, number))
						.brand(brands.get((int) (number % brands.size())))
						.arrivedAt(LocalDateTime.now().minusHours(number % 24))
						.build();

				// 배치 알고리즘이 "이 차가 언제 어느 출구로 나가는지" 를 알아야 기존 차의
				// 출차 경로를 그릴 수 있다. 실데이터가 없으므로 여기서 만들어 준다.
				vehicle.assignOutboundPlan(
						number % 2 == 0 ? NextMode.TRUCK : NextMode.RAIL,
						LocalDateTime.now().plusMinutes(cutoffMinutes(number, request)));
				vehicle.parkAt(slot);
				slot.markOccupied();
				vehicles.add(vehicle);
			}
			vehicleRepository.saveAll(vehicles);
			created += vehicles.size();
		}
		return created;
	}

	/**
	 * 출차 컷오프까지 남은 분. 차마다 흩어져야 알고리즘이 순서를 정할 거리가 생긴다.
	 * 범위를 안 주면 30분 ~ 10시간 사이에 고르게 편다.
	 */
	private long cutoffMinutes(long number, YardSeedRequest request) {
		long from = request.cutoffMinutesFromOrDefault();
		long to = request.cutoffMinutesToOrDefault();
		if (to <= from) {
			return from;
		}
		return from + (number * 7 % (to - from));
	}

	private Block block(String blockId) {
		return blockRepository.findById(blockId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
						"없는 블록입니다: " + blockId));
	}

	/** 이미 쓴 번호를 다시 내주지 않도록 최대 번호 다음부터 잇는다. */
	private long nextVehicleNumber() {
		return vehicleRepository.findMaxVehicleId()
				.map(id -> Long.parseLong(id.substring(id.indexOf('-') + 1)) + 1)
				.orElse(1L);
	}
}
