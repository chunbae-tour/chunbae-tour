package com.chunbaetour.domain.payment.type;

public enum PaymentOrderStatus {
    PENDING,    // 결제 요청 생성 후 사용자가 PG 결제창에서 결제 진행 중
    COMPLETED,  // PG 결제 완료 후 포트원 웹훅으로 승인 확인됨
    FAILED,     // PG 결제 실패 또는 승인 거절됨
    CANCELLED,  // 사용자가 결제창을 이탈하거나 결제 요청이 만료됨
    REFUNDED    // 관리자 환불 승인 후 PG 취소까지 완료됨
}
