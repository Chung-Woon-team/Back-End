package com.Chung_Woon.Chung_Woon.domain.plan;

import com.Chung_Woon.Chung_Woon.domain.vehicle.Vehicle;
import com.Chung_Woon.Chung_Woon.domain.vehicle.VehicleStatus;
import com.Chung_Woon.Chung_Woon.domain.yard.Block;
import com.Chung_Woon.Chung_Woon.domain.yard.Slot;
import com.Chung_Woon.Chung_Woon.domain.yard.SlotStatus;
import com.Chung_Woon.Chung_Woon.global.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlanReviewServiceTest {

	private final PlanRevisionRepository planRevisionRepository = mock(PlanRevisionRepository.class);
	private final PlacementRepository placementRepository = mock(PlacementRepository.class);
	private final MoveTaskRepository moveTaskRepository = mock(MoveTaskRepository.class);

	private PlanReviewService service;

	@BeforeEach
	void setUp() {
		service = new PlanReviewService(planRevisionRepository, placementRepository, moveTaskRepository);
	}

	@Test
	void approveTransitionsStatusAndRecordsReviewer() {
		PlanRevision revision = PlanRevision.builder().planVersion("B0-r1").status(PlanStatus.DRAFT).build();
		when(planRevisionRepository.findByPlanVersion("B0-r1")).thenReturn(Optional.of(revision));

		var summary = service.approve("B0-r1", "야드관리자A");

		assertThat(summary.status()).isEqualTo(PlanStatus.APPROVED);
		assertThat(summary.approvedBy()).isEqualTo("야드관리자A");
		assertThat(revision.getStatus()).isEqualTo(PlanStatus.APPROVED);
	}

	@Test
	void approveParksVehicleAndMarksSlotOccupied() {
		PlanRevision revision = PlanRevision.builder().planVersion("B0-r1").status(PlanStatus.DRAFT).build();
		when(planRevisionRepository.findByPlanVersion("B0-r1")).thenReturn(Optional.of(revision));

		Vehicle vehicle = Vehicle.builder().vehicleId("V-0001").status(VehicleStatus.EXPECTED).build();
		Block block = Block.builder().blockId("B01").closed(false).build();
		Slot newSlot = Slot.builder().slotId("B01-R04-C04").block(block).status(SlotStatus.EMPTY).build();
		Placement placement = Placement.builder().planRevision(revision).vehicle(vehicle).slot(newSlot).build();
		when(placementRepository.findAllByPlanVersion("B0-r1")).thenReturn(List.of(placement));

		service.approve("B0-r1", "야드관리자A");

		assertThat(vehicle.getCurrentSlot()).isEqualTo(newSlot);
		assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.IN_YARD);
		assertThat(newSlot.getStatus()).isEqualTo(SlotStatus.OCCUPIED);
	}

	@Test
	void approveFreesOldSlotWhenVehicleMoved() {
		PlanRevision revision = PlanRevision.builder().planVersion("B0-r2").status(PlanStatus.DRAFT).build();
		when(planRevisionRepository.findByPlanVersion("B0-r2")).thenReturn(Optional.of(revision));

		Block block = Block.builder().blockId("B01").closed(false).build();
		Slot oldSlot = Slot.builder().slotId("B01-R04-C04").block(block).status(SlotStatus.OCCUPIED).build();
		Slot newSlot = Slot.builder().slotId("B01-R04-C05").block(block).status(SlotStatus.EMPTY).build();
		Vehicle vehicle = Vehicle.builder().vehicleId("V-0001").status(VehicleStatus.IN_YARD)
				.currentSlot(oldSlot).build();
		Placement placement = Placement.builder().planRevision(revision).vehicle(vehicle).slot(newSlot).build();
		when(placementRepository.findAllByPlanVersion("B0-r2")).thenReturn(List.of(placement));

		service.approve("B0-r2", "야드관리자A");

		assertThat(vehicle.getCurrentSlot()).isEqualTo(newSlot);
		assertThat(oldSlot.getStatus()).isEqualTo(SlotStatus.EMPTY);
		assertThat(newSlot.getStatus()).isEqualTo(SlotStatus.OCCUPIED);
	}

	@Test
	void rejectTransitionsStatus() {
		PlanRevision revision = PlanRevision.builder().planVersion("B0-r1").status(PlanStatus.DRAFT).build();
		when(planRevisionRepository.findByPlanVersion("B0-r1")).thenReturn(Optional.of(revision));

		var summary = service.reject("B0-r1", "야드관리자A");

		assertThat(summary.status()).isEqualTo(PlanStatus.REJECTED);
	}

	@Test
	void throwsNotFoundForUnknownPlanVersion() {
		when(planRevisionRepository.findByPlanVersion("B9-r9")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.approve("B9-r9", "누군가"))
				.isInstanceOf(BusinessException.class);
	}

	@Test
	void listOrdersByIdDescending() {
		PlanRevision older = PlanRevision.builder().planVersion("B0").status(PlanStatus.APPROVED).build();
		PlanRevision newer = PlanRevision.builder().planVersion("B0-r1").status(PlanStatus.DRAFT).build();
		when(planRevisionRepository.findAllByOrderByIdDesc()).thenReturn(List.of(newer, older));

		var summaries = service.list();

		assertThat(summaries).extracting(PlanReviewService.PlanSummary::planVersion)
				.containsExactly("B0-r1", "B0");
	}
}
