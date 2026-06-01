package com.chunbaetour.domain.payment.type;

public enum PaymentOrderStatus {
    // 결제 대기
    PENDING,
    // 결제 완료
    COMPLETED,
    // 결제 실패
    FAILED,
    // 결제 취소
    CANCELLED,
    // 환불 완료
    REFUNDED,
    // 부분 취소 감지
    PARTIAL_CANCELLED,
    // 관리자 확인 필요
    ADJUSTMENT_REQUIRED
}
