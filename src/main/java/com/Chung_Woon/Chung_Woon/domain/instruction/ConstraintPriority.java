package com.Chung_Woon.Chung_Woon.domain.instruction;

public enum ConstraintPriority {
	/** 반드시 지켜야 함. 위반하는 배치는 거부된다. 장표의 "Hard Constraint 위반 0건" 지표 대상. */
	HARD,
	/** 지키면 좋음. 목적함수 페널티로만 반영된다. */
	SOFT
}
