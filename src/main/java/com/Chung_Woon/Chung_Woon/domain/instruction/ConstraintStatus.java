package com.Chung_Woon.Chung_Woon.domain.instruction;

public enum ConstraintStatus {
	/** 파싱은 됐지만 담당자 승인 대기. 이 상태에서는 배치에 절대 반영되지 않는다. */
	PENDING_REVIEW,
	APPROVED,
	REJECTED
}
