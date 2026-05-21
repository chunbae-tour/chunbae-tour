package com.chunbaetour.domain.payment.type;

public enum RefundStatus {
    PENDING,    // 환불 요청 접수, 관리자 검토 대기
    APPROVED,   // 관리자 승인 + PG 환불 완료
    REJECTED,   // 관리자 거절
    CANCELLED   // 사용자 취소
}
