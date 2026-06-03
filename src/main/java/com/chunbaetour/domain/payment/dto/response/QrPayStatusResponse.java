package com.chunbaetour.domain.payment.dto.response;

import com.chunbaetour.domain.payment.dto.response.QrPayCreateResponse.MenuSnapshotItem;
import com.chunbaetour.domain.payment.entity.QrPayRequest;
import com.chunbaetour.domain.payment.type.QrPayStatus;
import java.time.LocalDateTime;
import java.util.List;

public record QrPayStatusResponse(
        String payRequestId,
        QrPayStatus status,
        Long amount,
        Long shopId,
        List<MenuSnapshotItem> menuItems,
        LocalDateTime expiredAt,
        LocalDateTime completedAt
) {
    public static QrPayStatusResponse of(QrPayRequest req, List<MenuSnapshotItem> menuItems) {
        return new QrPayStatusResponse(
                req.getPayRequestId(),
                req.getStatus(),
                req.getAmount(),
                req.getShopId(),
                menuItems,
                req.getExpiredAt(),
                req.getCompletedAt()
        );
    }
}
