package com.Chung_Woon.Chung_Woon.domain.observation;

public enum ObservationSource {
	/** 고정 카메라 */
	FIXED_CAMERA,
	/** 드론 */
	DRONE,
	/** 현장 담당자 수기 입력 (LLM·비전 장애 시 폴백 경로) */
	MANUAL
}
