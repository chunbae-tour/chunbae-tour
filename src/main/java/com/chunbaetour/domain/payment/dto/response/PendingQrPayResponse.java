package com.chunbaetour.domain.payment.dto.response;

import com.chunbaetour.domain.payment.entity.QrPayRequest;
import java.time.LocalDateTime;

public record PendingQrPayResponse(
        String payRequestId,
        Long shopId,
        Long userId,
        Long amount,
        String menuItems,
        LocalDateTime createdAt,
        LocalDateTime expiredAt
) {
    public static PendingQrPayResponse from(QrPayRequest req) {
        return new PendingQrPayResponse(
                req.getPayRequestId(),
                req.getShopId(),
                req.getUserId(),
                req.getAmount(),
                req.getMenuItems(),
                req.getCreatedAt(),
                req.getExpiredAt()
        );
    }
}
