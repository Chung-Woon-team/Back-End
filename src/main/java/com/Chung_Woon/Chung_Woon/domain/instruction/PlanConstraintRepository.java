package com.Chung_Woon.Chung_Woon.domain.instruction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlanConstraintRepository extends JpaRepository<PlanConstraint, String> {

	List<PlanConstraint> findByStatus(ConstraintStatus status);

	List<PlanConstraint> findByInstruction_InstructionId(String instructionId);

	/** 최적화 엔진에 넘길 제약 — 승인된 것만. */
	default List<PlanConstraint> findApplicable() {
		return findByStatus(ConstraintStatus.APPROVED);
	}
}
