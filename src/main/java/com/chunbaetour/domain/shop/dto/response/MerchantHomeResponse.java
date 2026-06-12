package com.chunbaetour.domain.shop.dto.response;

import com.chunbaetour.domain.payment.repository.CompletedQrPayView;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record MerchantHomeResponse(
        long todaySalesAmount,
        long yesterdaySalesAmount,
        LocalDate todaySalesDate,
        List<HourlySales> hourlySales,
        long missedPaymentCount,
        List<RecentPaymentResponse> recentPayments
) {

    /**
     * 시간대별 매출 분포 한 칸 (KAN-283).
     * hour는 한국 영업일 기준 0~23시, amount는 해당 시간대 COMPLETED 결제 합계.
     * 매출이 없는 시간대도 amount 0으로 항상 24개를 채워 내려준다(프론트 차트 축 고정).
     */
    public record HourlySales(int hour, long amount) {
    }

    public record RecentPaymentResponse(
            String payRequestId,
            Long shopId,
            Long amount,
            LocalDateTime completedAt
    ) {

        /**
         * 완료 QR 결제 경량 뷰를 상인 홈의 최근 결제 응답으로 변환한다.
         *
         * <p>대시보드에는 완료 시각을 기준으로 노출해야 하므로 updatedAt이 아니라
         * 완료 시점 전용 컬럼 completedAt을 사용한다.
         *
         * @param view 최근 결제에 표시할 완료 QR 결제 뷰
         * @return 상인 홈 최근 결제 응답 DTO
         */
        public static RecentPaymentResponse from(CompletedQrPayView view) {
            return new RecentPaymentResponse(
                    view.getPayRequestId(),
                    view.getShopId(),
                    view.getAmount(),
                    view.getCompletedAt()
            );
        }
    }
}
