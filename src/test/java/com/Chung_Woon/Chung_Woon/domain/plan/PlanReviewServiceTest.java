package com.Chung_Woon.Chung_Woon.domain.plan;

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
