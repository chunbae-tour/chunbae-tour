package com.chunbaetour.domain.payment.dto.response;

import com.chunbaetour.domain.payment.entity.Refund;
import com.chunbaetour.domain.payment.type.RefundStatus;
import java.time.LocalDateTime;

/**
 * 사용자 환불 내역 조회 응답 DTO — GET /payments/refunds.
 * 관리자용 RefundDetailResponse(userId·관리자 처리 필드 포함)와 달리 사용자 노출 최소 필드만 포함.
 * rejectReason은 REJECTED 상태일 때만 값 존재, 나머지 null — 불필요한 정보 노출 방지.
 */
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
                refund.getStatus() == RefundStatus.REJECTED ? refund.getRejectReason() : null,
                refund.getCreatedAt()
        );
    }
}
