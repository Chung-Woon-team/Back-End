package com.Chung_Woon.Chung_Woon.domain.plan;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MoveTaskRepository extends JpaRepository<MoveTask, Long> {

	List<MoveTask> findByPlanRevision_PlanVersionOrderBySequenceAsc(String planVersion);
}
