package com.chunbaetour.domain.shop.type;

/**
 * 상인 인증 신청 상태 (KAN-204, Admin Epic KAN-177 S05).
 *
 * <p>상태 전이: {@code PENDING → APPROVED} (승인) / {@code PENDING → REJECTED} (거절).
 * 승인/거절은 PENDING에서만 가능 — {@link com.chunbaetour.domain.shop.entity.ShopCertification}의
 * 도메인 메서드가 상태 가드한다(PENDING 외 호출 시 409).
 *
 * <p>인증 취소(운영자가 이미 인증된 가게의 인증을 회수)는 신청 상태가 아니라 가게의 {@code is_certified}
 * 플래그를 토글하는 별도 액션이므로 본 enum에 별도 상태를 추가하지 않는다.
 */
public enum ShopCertificationStatus {

    /** 신청 접수 — 운영자 미처리. */
    PENDING,

    /** 운영자 승인 — Shop.markCertified() cascade 부여. */
    APPROVED,

    /** 운영자 거절 — rejectReason 기록. */
    REJECTED
}
