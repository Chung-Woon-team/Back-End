package com.Chung_Woon.Chung_Woon.domain.instruction;

/**
 * 파서가 만들 수 있는 제약 종류. 여기 없는 intent 는 거절한다(장표 8쪽 "미지원 Intent는 거절").
 * 종류를 늘릴 때는 파이썬 쪽 Pydantic 스키마의 ConstraintType 과 반드시 함께 고칠 것.
 */
public enum ConstraintType {
	/** 블록 폐쇄. 예: "3번 블록은 도색작업으로 폐쇄해" */
	BLOCK_CLOSURE,
	/** 속성 기준 묶음 배치. 예: "브랜드 B 차량은 서쪽으로 모아줘" */
	VEHICLE_GROUPING,
	/** 출고 우선순위. 예: "내일 오전 컷오프 차량은 게이트 가깝게" */
	OUTBOUND_PRIORITY
}
