package com.chunbaetour.domain.payment.dto.response;

import com.chunbaetour.domain.payment.entity.PaymentOrder;
import com.chunbaetour.domain.payment.type.PaymentMethod;
import com.chunbaetour.domain.payment.type.PaymentOrderStatus;
import java.time.LocalDateTime;

public record PaymentHistoryResponse(
        Long id,
        String orderUid,
        Long amount,
        PaymentMethod paymentMethod,
        PaymentOrderStatus status,
        LocalDateTime createdAt
) {
    public static PaymentHistoryResponse from(PaymentOrder order) {
        return new PaymentHistoryResponse(
                order.getId(),
                order.getOrderUid(),
                order.getAmount(),
                order.getPaymentMethod(),
                order.getStatus(),
                order.getCreatedAt()
        );
    }
}
