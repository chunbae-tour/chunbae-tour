package com.chunbaetour.domain.shop.dto.request;

import com.chunbaetour.domain.shop.type.ShopStatus;
import jakarta.validation.constraints.NotNull;

/**
 * 상인 가게 상태 변경 요청 DTO (KAN-213).
 * 허용 상태: ACTIVE, CLOSED. SUSPENDED는 서비스 레이어에서 차단.
 */
public record MerchantShopStatusRequest(
        @NotNull ShopStatus status
) {
}
