package com.Chung_Woon.Chung_Woon.domain.vehicle;

/**
 * 배에서 <b>내리는</b> 순서. 선하증권이 지정한다.
 *
 * <p>⚠️ {@link VehiclePriority}(URGENT/NORMAL) 와 헷갈리지 말 것. 그건 야드에서
 * <b>나가는</b> 긴급도이고 컷오프 기준이다. 축이 다르므로 절대 한 필드로 합치지 않는다.
 * 고데크 차량(P1)이 출고는 급하지 않을 수 있고, 그 반대도 있다.
 */
public enum UnloadingPriority {
	/** 최우선. 샘플에서는 고데크(높이 초과) 화물 */
	P1,
	/** 표준 */
	P2,
	/** 마지막. 샘플에서는 견인 필요 차량 */
	P3
}
