package com.Chung_Woon.Chung_Woon.domain.vehicle;

/**
 * 동력원. 배치에 실제로 영향을 준다 — EV 는 화재 대비 통로(EV FIRE LANE)가 필요하고,
 * 내연기관은 누유 점검 대상이라 QC 구역으로 빠지는 경우가 있다.
 */
public enum Powertrain {
	BATTERY_EV,
	GASOLINE,
	DIESEL,
	HYBRID
}
