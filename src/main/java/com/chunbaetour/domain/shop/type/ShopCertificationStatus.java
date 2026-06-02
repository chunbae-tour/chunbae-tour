package com.chunbaetour.domain.shop.type;

/**
 * 상인 인증 신청 상태 (KAN-204, Admin Epic KAN-177 S05).
 *
 * <p>상태 전이: {@code PENDING → APPROVED} (승인) / {@code PENDING → REJECTED} (거절) /
 * {@code APPROVED → CANCELLED} (취소). 승인/거절은 PENDING에서만, 취소는 APPROVED에서만 가능 —
 * {@link com.chunbaetour.domain.shop.entity.ShopCertification}의 도메인 메서드가 상태 가드한다(불일치 시 409).
 *
 * <p>인증 취소(운영자가 이미 인증된 가게의 인증을 회수)는 방향 B 채택: 인증 row에 CANCELLED 전이를 기록하고
 * cancelReason·processedBy·processedAt을 남긴다. 동시에 서비스가 {@code Shop.unmarkCertified()}로
 * {@code is_certified} 플래그도 회수한다.
 */
public enum ShopCertificationStatus {

    /** 신청 접수 — 운영자 미처리. */
    PENDING,

    /** 운영자 승인 — Shop.markCertified() cascade 부여. */
    APPROVED,

    /** 운영자 거절 — rejectReason 기록. */
    REJECTED,

    /** 운영자 취소 — APPROVED 인증 회수. cancelReason 기록 + Shop.unmarkCertified() cascade. */
    CANCELLED
}
