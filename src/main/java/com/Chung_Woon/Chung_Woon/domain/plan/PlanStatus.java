package com.Chung_Woon.Chung_Woon.domain.plan;

public enum PlanStatus {
	/** 계산은 끝났지만 아직 담당자에게 올리지 않음 */
	DRAFT,
	/** LangGraph 의 human_approval interrupt 지점에서 대기 중 */
	PENDING_APPROVAL,
	/** 승인됨. 이 판만 현장 작업지시로 나간다. */
	APPROVED,
	REJECTED
}
