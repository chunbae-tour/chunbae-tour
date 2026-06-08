package com.chunbaetour.domain.shop.dto.request;

import com.chunbaetour.domain.shop.type.ShopStatus;
import jakarta.validation.constraints.NotNull;

/**
 * 관리자 가게 상태 변경 요청 DTO.
 * CLOSED 상태로의 변경은 서비스 레이어에서 차단 — 폐업은 별도 처리.
 */
public record AdminShopStatusRequest(
        @NotNull ShopStatus status
) {
}
