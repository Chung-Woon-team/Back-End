package com.Chung_Woon.Chung_Woon.domain.yard;

public enum SlotStatus {
	/** 비어 있음 - 배치 가능 */
	EMPTY,
	/** 차량이 서 있음 */
	OCCUPIED,
	/** 물리적으로 못 쓰는 자리 (공사, 파손 등) */
	BLOCKED
}
