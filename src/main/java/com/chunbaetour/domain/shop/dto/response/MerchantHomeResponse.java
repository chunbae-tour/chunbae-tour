package com.chunbaetour.domain.shop.dto.response;

import com.chunbaetour.domain.payment.entity.QrPayRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record MerchantHomeResponse(
        long todaySalesAmount,
        LocalDate todaySalesDate,
        List<RecentPaymentResponse> recentPayments
) {

    public record RecentPaymentResponse(
            String payRequestId,
            Long shopId,
            Long amount,
            LocalDateTime completedAt
    ) {

        /**
         * QR 결제 요청 엔티티를 상인 홈의 최근 결제 응답으로 변환한다.
         *
         * <p>대시보드에는 완료 시각을 기준으로 노출해야 하므로 updatedAt이 아니라
         * 완료 시점 전용 컬럼 completedAt을 사용한다.
         *
         * @param request 최근 결제에 표시할 QR 결제 요청 엔티티
         * @return 상인 홈 최근 결제 응답 DTO
         */
        public static RecentPaymentResponse from(QrPayRequest request) {
            return new RecentPaymentResponse(
                    request.getPayRequestId(),
                    request.getShopId(),
                    request.getAmount(),
                    request.getCompletedAt()
            );
        }
    }
}
