package com.chunbaetour.domain.payment.dto.response;

import com.chunbaetour.domain.payment.entity.Refund;
import com.chunbaetour.domain.payment.type.RefundStatus;

/** 환불 요청 생성 응답 DTO */
public record RefundResponse(
        Long refundId,
        Long paymentOrderId,
        Long amount,
        RefundStatus status
) {
    public static RefundResponse from(Refund refund) {
        return new RefundResponse(
                refund.getId(),
                refund.getPaymentOrderId(),
                refund.getAmount(),
                refund.getStatus()
        );
    }
}
