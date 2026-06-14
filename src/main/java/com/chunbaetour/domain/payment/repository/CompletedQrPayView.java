package com.chunbaetour.domain.payment.repository;

import java.time.LocalDateTime;

/**
 * 상인 홈 대시보드 집계용 완료 QR 결제 경량 조회 뷰 (KAN-283).
 * menu_items 같은 무거운 JSON 컬럼을 제외하고, 매출 합계·시간대 분포·최근 결제 산출에 필요한 필드만 노출한다.
 */
public interface CompletedQrPayView {
    String getPayRequestId();

    Long getShopId();

    Long getAmount();

    LocalDateTime getCompletedAt();
}
