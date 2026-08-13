package com.Chung_Woon.Chung_Woon.domain.plan;

import com.Chung_Woon.Chung_Woon.ai.dto.ReplanRequest;
import com.Chung_Woon.Chung_Woon.domain.vehicle.VehicleRepository;
import com.Chung_Woon.Chung_Woon.domain.vehicle.VehicleStatus;
import com.Chung_Woon.Chung_Woon.domain.yard.BlockRepository;
import com.Chung_Woon.Chung_Woon.domain.yard.SlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 파이썬에 넘길 야드 현황·차량 목록을 스프링 DB에서 직접 모은다 — 파이썬은 뭐가 유효한 값인지
 * 알 방법이 없다(docs/API_CONTRACT.md). {@code GET /api/yard/state}, {@code GET /api/vehicles}
 * 도 이 서비스를 그대로 쓴다.
 */
@Service
@RequiredArgsConstructor
public class YardQueryService {

	private final BlockRepository blockRepository;
	private final SlotRepository slotRepository;
	private final VehicleRepository vehicleRepository;
	private final PlanRevisionRepository planRevisionRepository;
	private final PlacementRepository placementRepository;

	@Transactional(readOnly = true)
	public ReplanRequest.YardState currentYardState() {
		List<ReplanRequest.BlockPayload> blocks = blockRepository.findAll().stream()
				.map(b -> new ReplanRequest.BlockPayload(b.getBlockId(), b.isClosed()))
				.toList();

		List<ReplanRequest.SlotPayload> slots = slotRepository.findAll().stream()
				.map(s -> new ReplanRequest.SlotPayload(s.getSlotId(), s.getBlock().getBlockId(), s.getStatus().name()))
				.toList();

		return new ReplanRequest.YardState(blocks, slots, currentPlacements());
	}

	/** 가장 최근에 승인된 판의 배치. 승인된 판이 아직 없으면(첫 배치) 빈 맵이다. */
	@Transactional(readOnly = true)
	public Map<String, String> currentPlacements() {
		return planRevisionRepository.findFirstByStatusOrderByApprovedAtDesc(PlanStatus.APPROVED)
				.map(plan -> placementRepository.findAllByPlanVersion(plan.getPlanVersion()).stream()
						.collect(Collectors.toMap(p -> p.getVehicle().getVehicleId(), p -> p.getSlot().getSlotId())))
				.orElseGet(Map::of);
	}

	/** 배치 계산 대상 — 이미 나간 차는 뺀다({@code Vehicle.isPlannable()} 과 같은 기준). */
	@Transactional(readOnly = true)
	public List<ReplanRequest.VehiclePayload> plannableVehicles() {
		return vehicleRepository.findByStatusNot(VehicleStatus.DEPARTED).stream()
				.map(v -> new ReplanRequest.VehiclePayload(
						v.getVehicleId(),
						v.getStatus().name(),
						v.getPriority().name(),
						v.getNextMode() != null ? v.getNextMode().name() : null,
						v.getDepartureCutoffAt(),
						v.getBrand(),
						v.getDischargeSequence()
				))
				.toList();
	}
}
