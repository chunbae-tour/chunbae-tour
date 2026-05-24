package com.chunbaetour.domain.payment.dto.response;

import com.chunbaetour.domain.payment.entity.Refund;
import com.chunbaetour.domain.payment.type.RefundStatus;
import java.time.LocalDateTime;

/** 사용자 환불 내역 조회 응답 DTO — GET /payments/refunds. RefundDetailResponse(관리자용, userId 포함)와 구분. */
public record UserRefundResponse(
        Long refundId,
        Long paymentOrderId,
        Long amount,
        RefundStatus status,
        String reason,
        String rejectReason,  // REJECTED 상태일 때만 값 존재, 나머지 null
        LocalDateTime createdAt
) {
    public static UserRefundResponse from(Refund refund) {
        return new UserRefundResponse(
                refund.getId(),
                refund.getPaymentOrderId(),
                refund.getAmount(),
                refund.getStatus(),
                refund.getReason(),
                refund.getRejectReason(),
                refund.getCreatedAt()
        );
    }
}
