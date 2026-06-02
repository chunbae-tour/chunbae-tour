package com.chunbaetour.domain.payment.dto.response;

import com.chunbaetour.domain.payment.entity.QrPayRequest;
import com.chunbaetour.domain.payment.type.QrPayStatus;
import java.time.LocalDateTime;

public record QrPayStatusResponse(
        String payRequestId,
        QrPayStatus status,
        Long amount,
        Long shopId,
        LocalDateTime expiredAt,
        LocalDateTime completedAt
) {
    public static QrPayStatusResponse from(QrPayRequest req) {
        return new QrPayStatusResponse(
                req.getPayRequestId(),
                req.getStatus(),
                req.getAmount(),
                req.getShopId(),
                req.getExpiredAt(),
                req.getCompletedAt()
        );
    }
}
