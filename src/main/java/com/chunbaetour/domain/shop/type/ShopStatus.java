package com.chunbaetour.domain.shop.type;

public enum ShopStatus {
    ACTIVE,
    /** 관리자 신고 처리 또는 운영 정지 상태 — 공개 노출 차단, 상인 수정 불가 */
    SUSPENDED,
    CLOSED
}
