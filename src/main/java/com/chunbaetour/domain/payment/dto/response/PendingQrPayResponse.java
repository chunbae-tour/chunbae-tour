package com.chunbaetour.domain.payment.dto.response;

import com.chunbaetour.domain.payment.dto.response.QrPayCreateResponse.MenuSnapshotItem;
import com.chunbaetour.domain.payment.entity.QrPayRequest;
import java.time.LocalDateTime;
import java.util.List;

public record PendingQrPayResponse(
        String payRequestId,
        Long shopId,
        Long amount,
        List<MenuSnapshotItem> menuItems,
        LocalDateTime createdAt,
        LocalDateTime expiredAt
) {
    public static PendingQrPayResponse from(QrPayRequest req, List<MenuSnapshotItem> menuItems) {
        return new PendingQrPayResponse(
                req.getPayRequestId(),
                req.getShopId(),
                req.getAmount(),
                menuItems,
                req.getCreatedAt(),
                req.getExpiredAt()
        );
    }
}
