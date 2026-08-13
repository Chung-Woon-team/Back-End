package com.Chung_Woon.Chung_Woon.domain.yard;

import com.Chung_Woon.Chung_Woon.ai.AiClient;
import com.Chung_Woon.Chung_Woon.ai.dto.GridObservationResponse;
import com.Chung_Woon.Chung_Woon.api.dto.OccupancyCheckResponse;
import com.Chung_Woon.Chung_Woon.api.dto.OccupancyConfirmResponse;
import com.Chung_Woon.Chung_Woon.domain.observation.ObservationSnapshot;
import com.Chung_Woon.Chung_Woon.domain.observation.ObservationSnapshotRepository;
import com.Chung_Woon.Chung_Woon.domain.observation.ObservationSource;
import com.Chung_Woon.Chung_Woon.domain.vehicle.Vehicle;
import com.Chung_Woon.Chung_Woon.domain.vehicle.VehicleRepository;
import com.Chung_Woon.Chung_Woon.global.config.AiClientConfig;
import com.Chung_Woon.Chung_Woon.global.error.BusinessException;
import com.Chung_Woon.Chung_Woon.global.error.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * "야드 사진 한 장" → "지금 DB 상태와 뭐가 다른지" → "담당자가 고르면 그대로 반영".
 *
 * <p>사진은 슬롯별 비었다/찼다만 알려주고 어느 차인지는 모른다. 그래서 확정(PHOTO)해도
 * <b>새로 찬 슬롯</b>은 {@link Slot#markOccupied()} 만 하고 어떤 {@link Vehicle} 도 연결하지 않는다.
 * 반대로 <b>새로 빈 슬롯</b>은 거기 서 있던 차를 찾아 {@link Vehicle#leaveSlot()} 까지 같이 한다 —
 * 안 그러면 빈 슬롯인데 어떤 차가 거기 서 있다고 DB 가 계속 우기는 꼴이 된다.
 */
@Service
@RequiredArgsConstructor
public class YardOccupancyReviewService {

	private final AiClient aiClient;
	private final SlotRepository slotRepository;
	private final BlockRepository blockRepository;
	private final VehicleRepository vehicleRepository;
	private final ObservationSnapshotRepository observationSnapshotRepository;
	private final TransactionTemplate transactionTemplate;

	@Qualifier(AiClientConfig.AI_OBJECT_MAPPER)
	private final ObjectMapper aiObjectMapper;

	/**
	 * AI 호출은 트랜잭션 밖이다(InstructionParsingService 와 같은 이유 — 느린 호출 동안 DB 커넥션을
	 * 붙잡아두지 않으려는 것). 저장 + 대조만 트랜잭션으로 감싼다.
	 */
	public OccupancyCheckResponse checkOccupancy(MultipartFile file, ObservationSource sourceType) {
		GridObservationResponse observation = aiClient.extractGridObservation(file, sourceType);
		String batchId = UUID.randomUUID().toString();

		OccupancyDiff diff = transactionTemplate.execute(status -> {
			persistSnapshots(batchId, observation);
			return computeDiff(batchId);
		});
		return new OccupancyCheckResponse(
				diff.batchId(), diff.diffs().size(), diff.capturedAt(), diff.confidence(), diff.requiresConfirmation());
	}

	/**
	 * 담당자의 선택을 반영한다. diff 는 클라이언트가 들고 있던 걸 믿지 않고 <b>이 시점 DB로
	 * 다시 계산</b>한다 — 사진을 올린 뒤 확정하기 전 사이에 다른 경로로 DB 가 바뀌었을 수 있어서다.
	 */
	@Transactional
	public OccupancyConfirmResponse confirm(String batchId, OccupancyChoice choice) {
		OccupancyDiff freshDiff = computeDiff(batchId);

		if (choice == OccupancyChoice.KEEP) {
			return new OccupancyConfirmResponse(batchId, choice, 0);
		}

		int applied = 0;
		for (SlotDiff diff : freshDiff.diffs()) {
			Slot slot = slotRepository.findById(diff.slotId())
					.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "슬롯을 찾을 수 없습니다: " + diff.slotId()));

			if (SlotStatus.OCCUPIED.name().equals(diff.photoStatus())) {
				slot.markOccupied();
			} else {
				slot.markEmpty();
				vehicleRepository.findByCurrentSlot_SlotId(slot.getSlotId()).ifPresent(Vehicle::leaveSlot);
			}
			applied++;
		}
		return new OccupancyConfirmResponse(batchId, choice, applied);
	}

	private void persistSnapshots(String batchId, GridObservationResponse observation) {
		Map<String, List<GridObservationResponse.GridCell>> cellsByBlock = groupByBlock(observation.grid());

		for (Map.Entry<String, List<GridObservationResponse.GridCell>> entry : cellsByBlock.entrySet()) {
			Block block = blockRepository.findById(entry.getKey())
					.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "블록을 찾을 수 없습니다: " + entry.getKey()));

			observationSnapshotRepository.save(ObservationSnapshot.builder()
					.batchId(batchId)
					.block(block)
					.source(observation.sourceType())
					.capturedAt(observation.capturedAt())
					.gridJson(writeJson(entry.getValue()))
					.confidence(observation.confidence() != null ? observation.confidence() : 0.0)
					.requiresConfirmation(Boolean.TRUE.equals(observation.requiresConfirmation()))
					.build());
		}
	}

	/** 호출 시점 마다 스냅샷을 다시 읽어 지금 Slot.status 와 비교한다 - diff 자체는 저장하지 않는다. */
	private OccupancyDiff computeDiff(String batchId) {
		List<ObservationSnapshot> snapshots = observationSnapshotRepository.findAllByBatchId(batchId);
		if (snapshots.isEmpty()) {
			throw new BusinessException(ErrorCode.NOT_FOUND, "관측 배치를 찾을 수 없습니다: " + batchId);
		}

		List<SlotDiff> diffs = new ArrayList<>();
		for (ObservationSnapshot snapshot : snapshots) {
			String blockId = snapshot.getBlock().getBlockId();
			for (GridObservationResponse.GridCell cell : readJson(snapshot.getGridJson())) {
				String slotId = YardGrid.slotId(cell.row(), cell.col());
				SlotStatus dbStatus = slotRepository.findById(slotId)
						.map(Slot::getStatus)
						.orElse(SlotStatus.EMPTY);
				SlotStatus photoStatus = Boolean.TRUE.equals(cell.occupied()) ? SlotStatus.OCCUPIED : SlotStatus.EMPTY;

				if (dbStatus != photoStatus) {
					diffs.add(new SlotDiff(slotId, blockId, dbStatus.name(), photoStatus.name()));
				}
			}
		}

		ObservationSnapshot any = snapshots.get(0);
		return new OccupancyDiff(
				batchId, diffs, any.getCapturedAt(), any.getConfidence(), any.isRequiresConfirmation());
	}

	/** 절대좌표만 있는 칸을 블록별로 나눈다 - ObservationSnapshot 이 블록 단위 행이라서 필요하다. */
	private Map<String, List<GridObservationResponse.GridCell>> groupByBlock(List<GridObservationResponse.GridCell> cells) {
		Map<String, List<GridObservationResponse.GridCell>> map = new HashMap<>();
		for (GridObservationResponse.GridCell cell : cells) {
			BlockLayout layout = YardGrid.blockAt(cell.row(), cell.col())
					.orElseThrow(() -> new BusinessException(ErrorCode.AI_RESPONSE_INVALID,
							"(%d, %d) 은(는) 도로 칸이라 슬롯이 없습니다".formatted(cell.row(), cell.col())));
			map.computeIfAbsent(layout.blockId(), k -> new ArrayList<>()).add(cell);
		}
		return map;
	}

	private String writeJson(Object value) {
		try {
			return aiObjectMapper.writeValueAsString(value);
		} catch (JsonProcessingException e) {
			throw new UncheckedIOException("JSON 직렬화 실패", e);
		}
	}

	private List<GridObservationResponse.GridCell> readJson(String json) {
		try {
			return aiObjectMapper.readValue(json, new TypeReference<List<GridObservationResponse.GridCell>>() {
			});
		} catch (IOException e) {
			throw new UncheckedIOException("JSON 역직렬화 실패", e);
		}
	}
}
