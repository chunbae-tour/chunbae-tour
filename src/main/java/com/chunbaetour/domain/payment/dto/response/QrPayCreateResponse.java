package com.chunbaetour.domain.payment.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/** QR 결제 요청 생성 응답 — 상인 승인 대기 중 상태로 반환 */
public record QrPayCreateResponse(
        String payRequestId,
        Long shopId,
        String shopName,
        Long totalAmount,
        List<MenuSnapshotItem> menuItems,
        LocalDateTime expiredAt
) {

    /** 결제 시점 메뉴 스냅샷 항목 — 이후 메뉴 수정/삭제와 무관하게 결제 금액 보존 */
    public record MenuSnapshotItem(Long menuId, String name, Long price, int quantity) {}
}
