package com.Chung_Woon.Chung_Woon.domain.yard;

import com.Chung_Woon.Chung_Woon.ai.AiClient;
import com.Chung_Woon.Chung_Woon.ai.dto.GridObservationResponse;
import com.Chung_Woon.Chung_Woon.domain.observation.ObservationSnapshot;
import com.Chung_Woon.Chung_Woon.domain.observation.ObservationSnapshotRepository;
import com.Chung_Woon.Chung_Woon.domain.observation.ObservationSource;
import com.Chung_Woon.Chung_Woon.domain.vehicle.Vehicle;
import com.Chung_Woon.Chung_Woon.domain.vehicle.VehicleRepository;
import com.Chung_Woon.Chung_Woon.domain.vehicle.VehicleStatus;
import com.Chung_Woon.Chung_Woon.global.error.BusinessException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DB/AI 없이 순수 로직만: 사진 인식 결과를 블록별로 갈라 저장하고, 지금 Slot.status 와 대조하고,
 * 확정 시 Slot/Vehicle 을 갱신하는 부분. {@code transactionTemplate.execute(...)} 는 콜백만 바로
 * 실행하도록 스텁한다(InstructionParsingServiceTest 와 같은 패턴).
 */
class YardOccupancyReviewServiceTest {

	private final AiClient aiClient = mock(AiClient.class);
	private final SlotRepository slotRepository = mock(SlotRepository.class);
	private final BlockRepository blockRepository = mock(BlockRepository.class);
	private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
	private final ObservationSnapshotRepository observationSnapshotRepository = mock(ObservationSnapshotRepository.class);
	private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);

	private final List<ObservationSnapshot> savedSnapshots = new ArrayList<>();

	private YardOccupancyReviewService service;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		ObjectMapper aiObjectMapper = new ObjectMapper()
				.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
				.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		service = new YardOccupancyReviewService(
				aiClient, slotRepository, blockRepository, vehicleRepository,
				observationSnapshotRepository, transactionTemplate, aiObjectMapper);

		when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
			TransactionCallback<Object> callback = invocation.getArgument(0);
			return callback.doInTransaction(null);
		});

		when(observationSnapshotRepository.save(any())).thenAnswer(invocation -> {
			ObservationSnapshot snapshot = invocation.getArgument(0);
			savedSnapshots.add(snapshot);
			return snapshot;
		});
		when(observationSnapshotRepository.findAllByBatchId(anyString())).thenAnswer(invocation -> savedSnapshots);

		when(blockRepository.findById("B01")).thenReturn(Optional.of(Block.from(BlockLayout.B01)));
	}

	private MultipartFile _upload() {
		return new MockMultipartFile("file", "yard.png", "image/png", "fake-bytes".getBytes());
	}

	private GridObservationResponse _observation(GridObservationResponse.GridCell... cells) {
		return new GridObservationResponse(
				ObservationSource.MANUAL,
				LocalDateTime.parse("2026-08-13T14:00:00"),
				List.of(cells),
				0.9,
				false);
	}

	@Test
	void checkOccupancyReportsDiffWhenPhotoSaysOccupiedButDbIsEmpty() {
		Slot slot = Slot.at(Block.from(BlockLayout.B01), 4, 4); // EMPTY by default
		when(slotRepository.findById("B01-R04-C04")).thenReturn(Optional.of(slot));
		when(aiClient.extractGridObservation(any(), any())).thenReturn(
				_observation(new GridObservationResponse.GridCell(4, 4, true)));

		var response = service.checkOccupancy(_upload(), ObservationSource.MANUAL);

		// 상세 목록은 API 로 안 나간다 - 몇 곳이 다른지(diff_count)만 준다. 실제로 어느 슬롯이
		// 어떻게 바뀌는지는 confirm() 쪽 테스트(applied count·Slot/Vehicle 상태)가 검증한다.
		assertThat(response.diffCount()).isEqualTo(1);
		assertThat(response.confidence()).isEqualTo(0.9);
		assertThat(response.requiresConfirmation()).isFalse();
	}

	@Test
	void checkOccupancyReportsNoDiffWhenPhotoMatchesDb() {
		Slot slot = Slot.at(Block.from(BlockLayout.B01), 4, 4);
		slot.markOccupied();
		when(slotRepository.findById("B01-R04-C04")).thenReturn(Optional.of(slot));
		when(aiClient.extractGridObservation(any(), any())).thenReturn(
				_observation(new GridObservationResponse.GridCell(4, 4, true)));

		var response = service.checkOccupancy(_upload(), ObservationSource.MANUAL);

		assertThat(response.diffCount()).isZero();
	}

	@Test
	void checkOccupancyRejectsRoadCell() {
		when(aiClient.extractGridObservation(any(), any())).thenReturn(
				_observation(new GridObservationResponse.GridCell(0, 0, true)));

		assertThatThrownBy(() -> service.checkOccupancy(_upload(), ObservationSource.MANUAL))
				.isInstanceOf(BusinessException.class);
	}

	@Test
	void confirmWithPhotoMarksSlotOccupiedWithoutLinkingAnyVehicle() {
		Slot slot = Slot.at(Block.from(BlockLayout.B01), 4, 4);
		when(slotRepository.findById("B01-R04-C04")).thenReturn(Optional.of(slot));
		when(aiClient.extractGridObservation(any(), any())).thenReturn(
				_observation(new GridObservationResponse.GridCell(4, 4, true)));
		service.checkOccupancy(_upload(), ObservationSource.MANUAL);

		var result = service.confirm("any-batch-id", OccupancyChoice.PHOTO);

		assertThat(result.appliedCount()).isEqualTo(1);
		assertThat(slot.getStatus()).isEqualTo(SlotStatus.OCCUPIED);
	}

	@Test
	void confirmWithPhotoFreesSlotAndDetachesParkedVehicle() {
		Slot slot = Slot.at(Block.from(BlockLayout.B01), 4, 4);
		slot.markOccupied();
		Vehicle vehicle = Vehicle.builder().vehicleId("V-0001").status(VehicleStatus.IN_YARD)
				.currentSlot(slot).build();
		when(slotRepository.findById("B01-R04-C04")).thenReturn(Optional.of(slot));
		when(vehicleRepository.findByCurrentSlot_SlotId("B01-R04-C04")).thenReturn(Optional.of(vehicle));
		when(aiClient.extractGridObservation(any(), any())).thenReturn(
				_observation(new GridObservationResponse.GridCell(4, 4, false)));
		service.checkOccupancy(_upload(), ObservationSource.MANUAL);

		service.confirm("any-batch-id", OccupancyChoice.PHOTO);

		assertThat(slot.getStatus()).isEqualTo(SlotStatus.EMPTY);
		assertThat(vehicle.getCurrentSlot()).isNull();
	}

	@Test
	void confirmWithKeepChangesNothing() {
		Slot slot = Slot.at(Block.from(BlockLayout.B01), 4, 4);
		when(slotRepository.findById("B01-R04-C04")).thenReturn(Optional.of(slot));
		when(aiClient.extractGridObservation(any(), any())).thenReturn(
				_observation(new GridObservationResponse.GridCell(4, 4, true)));
		service.checkOccupancy(_upload(), ObservationSource.MANUAL);

		var result = service.confirm("any-batch-id", OccupancyChoice.KEEP);

		assertThat(result.appliedCount()).isZero();
		assertThat(slot.getStatus()).isEqualTo(SlotStatus.EMPTY);
	}
}
