package com.chunbaetour.domain.shop.type;

public enum ShopStatus {
    ACTIVE,
    SUSPENDED,
    CLOSED,
    /** 관리자 신고 처리 비공개 — SUSPENDED(자체 정지)와 구분. */
    HIDDEN
}
