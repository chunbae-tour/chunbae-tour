package com.chunbaetour.domain.payment.type;

public enum QrPayStatus {
    PENDING,    // 상인 응답 대기 중
    COMPLETED,  // 결제 완료
    REJECTED,   // 상인 거절
    EXPIRED,    // 5분 타임아웃 만료
    CANCELLED   // 사용자 직접 취소 (KAN-252)
}
