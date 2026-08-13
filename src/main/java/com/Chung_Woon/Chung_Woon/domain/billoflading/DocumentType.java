package com.Chung_Woon.Chung_Woon.domain.billoflading;

/**
 * 선적 서류 종류. 샘플 3장이 각각 다른 종류라 구분이 필요하다.
 * 야드 배치 로직에는 영향이 없지만, 화면에 문서 종류를 표시하고 원본 대조를 하려면 있어야 한다.
 */
public enum DocumentType {
	/** SHIPPED ON BOARD BILL OF LADING — 유통 가능, 원본 3부 */
	BILL_OF_LADING,
	/** SEA WAYBILL — 비유통, 원본 없음 */
	SEA_WAYBILL,
	/** STRAIGHT BILL OF LADING — 기명식, 비유통 */
	STRAIGHT_BILL_OF_LADING
}
