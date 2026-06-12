package com.chunbaetour.domain.shop.dto.response;

import com.chunbaetour.domain.payment.repository.CompletedQrPayView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 상인 홈 대시보드 응답 (KAN-283).
 *
 * <p><b>타임존 주의</b>: 집계성 필드(todaySalesDate, hourlySales)는 한국 영업일/시(KST) 기준이고,
 * 원시 타임스탬프(recentPayments[].completedAt)는 서버 저장 그대로 UTC다(API 전역 관례 — 프론트에서 KST로 렌더).
 * 따라서 같은 응답에서 hourlySales의 KST 시와 completedAt의 UTC 시각은 9시간 차이가 날 수 있다.
 */
public record MerchantHomeResponse(
        @Schema(description = "오늘(KST 영업일) 완료 매출 합계(원)")
        long todaySalesAmount,
        @Schema(description = "어제(KST 영업일) 완료 매출 합계(원)")
        long yesterdaySalesAmount,
        @Schema(description = "오늘 기준 영업일(KST)")
        LocalDate todaySalesDate,
        @Schema(description = "KST 0~23시 시간대별 완료 매출 분포(항상 24개)")
        List<HourlySales> hourlySales,
        @Schema(description = "오늘(KST) 미완료(거절+만료) 결제 건수")
        long missedPaymentCount,
        List<RecentPaymentResponse> recentPayments
) {

    /**
     * 시간대별 매출 분포 한 칸 (KAN-283).
     * hour는 한국 영업일 기준 0~23시, amount는 해당 시간대 COMPLETED 결제 합계.
     * 매출이 없는 시간대도 amount 0으로 항상 24개를 채워 내려준다(프론트 차트 축 고정).
     */
    public record HourlySales(
            @Schema(description = "시(KST 기준, 0~23)") int hour,
            @Schema(description = "해당 시간대 완료 매출 합계(원)") long amount) {
    }

    public record RecentPaymentResponse(
            String payRequestId,
            Long shopId,
            Long amount,
            @Schema(description = "결제 완료 시각(UTC). 프론트에서 KST로 렌더링")
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
