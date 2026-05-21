package com.chunbaetour.domain.payment.dto.response;

import com.chunbaetour.domain.payment.entity.Refund;
import com.chunbaetour.domain.payment.type.RefundStatus;
import java.time.LocalDateTime;

/** 관리자 환불 목록/상세 응답 DTO */
public record RefundDetailResponse(
        Long refundId,
        Long paymentOrderId,
        Long userId,
        Long amount,
        RefundStatus status,
        String reason,
        LocalDateTime createdAt
) {
    public static RefundDetailResponse from(Refund refund) {
        return new RefundDetailResponse(
                refund.getId(),
                refund.getPaymentOrderId(),
                refund.getUserId(),
                refund.getAmount(),
                refund.getStatus(),
                refund.getReason(),
                refund.getCreatedAt()
        );
    }
}
