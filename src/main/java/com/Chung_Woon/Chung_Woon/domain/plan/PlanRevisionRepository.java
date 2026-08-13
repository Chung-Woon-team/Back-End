package com.Chung_Woon.Chung_Woon.domain.plan;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanRevisionRepository extends JpaRepository<PlanRevision, Long> {

	Optional<PlanRevision> findByPlanVersion(String planVersion);

	List<PlanRevision> findByStatusOrderByIdDesc(PlanStatus status);

	/** Revision Log 화면용 - 최신 판부터. */
	List<PlanRevision> findAllByOrderByIdDesc();

	/** 현재 현장에 나가 있는 계획 = 가장 최근에 승인된 판. */
	Optional<PlanRevision> findFirstByStatusOrderByApprovedAtDesc(PlanStatus status);
}
