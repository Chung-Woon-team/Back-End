package com.Chung_Woon.Chung_Woon.domain.yard;

/** 사진과 DB 가 다를 때 담당자가 고르는 것. */
public enum OccupancyChoice {

	/** 사진 버전으로 확정 - DB(Slot·Vehicle)를 사진에 맞춰 바꾼다. */
	PHOTO,

	/** 지금 DB 를 유지 - 아무것도 바꾸지 않는다. */
	KEEP
}
