package com.chunbaetour.domain.payment.type;

public enum PaymentOrderStatus {
    PENDING,
    COMPLETED,
    FAILED,
    CANCELLED,
    REFUNDED,
    PARTIAL_CANCELLED,
    ADJUSTMENT_REQUIRED
}
