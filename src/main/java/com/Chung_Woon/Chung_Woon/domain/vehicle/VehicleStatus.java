package com.Chung_Woon.Chung_Woon.domain.vehicle;

/**
 * 차량이 지금 어디 단계에 있는지.
 *
 * <p>배치 최적화는 {@link #IN_YARD} 와 {@link #EXPECTED} 만 대상으로 한다.
 * {@link #DEPARTED} 차량을 계산에 넣으면 이미 나간 차를 옮기라는 작업지시가 나간다.
 */
public enum VehicleStatus {
	/** 선하증권에는 있지만 아직 하선 전 */
	EXPECTED,
	/** 야드에 주차 중 */
	IN_YARD,
	/** 야드를 떠남 (트럭·철도·선박에 실림) */
	DEPARTED
}
