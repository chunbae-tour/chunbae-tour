package com.chunbaetour.domain.shop.dto.response;

import com.chunbaetour.domain.payment.entity.QrPayRequest;
import java.time.LocalDateTime;
import java.util.List;

public record MerchantHomeResponse(
        Long todaySalesAmount,
        List<RecentPaymentResponse> recentPayments
) {

    public record RecentPaymentResponse(
            String payRequestId,
            Long shopId,
            Long amount,
            LocalDateTime completedAt
    ) {

        public static RecentPaymentResponse from(QrPayRequest request) {
            return new RecentPaymentResponse(
                    request.getPayRequestId(),
                    request.getShopId(),
                    request.getAmount(),
                    request.getUpdatedAt()
            );
        }
    }
}
