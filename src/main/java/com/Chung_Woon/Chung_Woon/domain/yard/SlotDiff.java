package com.Chung_Woon.Chung_Woon.domain.yard;

/**
 * 슬롯 하나에서 사진과 DB 가 다른 것. API 응답에는 안 나간다 — {@code /check} 는 diff_count 만
 * 보여주고, 실제 목록은 {@code /confirm} 이 내부적으로 적용할 때만 쓴다.
 */
public record SlotDiff(String slotId, String blockId, String dbStatus, String photoStatus) {
}
