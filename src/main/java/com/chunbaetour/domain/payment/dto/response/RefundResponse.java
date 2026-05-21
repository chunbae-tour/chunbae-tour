package com.chunbaetour.domain.payment.dto.response;

import com.chunbaetour.domain.payment.entity.Refund;
import com.chunbaetour.domain.payment.type.RefundStatus;

// TODO [조회 API 추가 시]: createdAt 필드 추가 — 클라이언트의 7일 만료 기준 표시 및 정렬에 필요. BaseEntity에 이미 있음.
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
