package com.chunbaetour.domain.payment.dto.response;

import com.chunbaetour.domain.payment.dto.response.QrPayCreateResponse.MenuSnapshotItem;
import com.chunbaetour.domain.payment.entity.QrPayRequest;
import com.chunbaetour.domain.payment.type.QrPayStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * QR 결제 상태 폴링 응답.
 *
 * <p>{@code completedAt}은 상인 승인으로 COMPLETED 전이된 경우에만 값을 가진다.
 * PENDING/REJECTED/EXPIRED/CANCELLED 상태에서는 {@code completedAt=null}이다 (프론트 분기 시 주의).
 */
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
