package com.Chung_Woon.Chung_Woon.domain.instruction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PlanConstraintRepository extends JpaRepository<PlanConstraint, String> {

	List<PlanConstraint> findByStatus(ConstraintStatus status);

	List<PlanConstraint> findByInstruction_InstructionId(String instructionId);

	/** 최적화 엔진에 넘길 제약 — 승인된 것만. */
	default List<PlanConstraint> findApplicable() {
		return findByStatus(ConstraintStatus.APPROVED);
	}

	/**
	 * 지금까지 발급된 가장 큰 제약 ID. 없으면 비어 있다.
	 *
	 * <p>새 ID 를 만들 때 {@code count()} 를 쓰면 안 된다 — 행이 한 번이라도 삭제되면 이미 쓰인
	 * 번호를 다시 내주고, PK 가 수동 할당이라 저장이 merge(UPSERT)로 동작해서 기존 행을 예외
	 * 없이 덮어쓴다(BillOfLadingExpansionService 에서 실측된 결함과 같은 패턴).
	 */
	@Query("select max(c.constraintId) from PlanConstraint c")
	Optional<String> findMaxConstraintId();
}
