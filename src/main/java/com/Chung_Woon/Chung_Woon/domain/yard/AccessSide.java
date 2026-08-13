package com.Chung_Woon.Chung_Woon.domain.yard;

/**
 * 슬롯이 어느 쪽 도로에서 들어가는 자리인지.
 *
 * <p>블록은 네 면이 모두 도로에 닿아 있어서 한 레인(세로 열, 22칸)은 위·아래 양쪽에서 진입한다.
 * 위쪽 11칸은 {@code NORTH}, 아래쪽 11칸은 {@code SOUTH} 로 나누고 각각 depth 0~10 을 매긴다.
 * 이게 없으면 (블록, 레인, depth) 만으로는 두 자리가 겹친다.
 */
public enum AccessSide {

	/** 행 번호가 작은 쪽 도로에서 진입. */
	NORTH,

	/** 행 번호가 큰 쪽 도로에서 진입. */
	SOUTH
}
