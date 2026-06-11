package com.chunbaetour.domain.shop.dto.request;

import jakarta.validation.constraints.Positive;

/**
 * 관리자 가게-전통시장 수동 연결 요청 DTO (KAN-268).
 * traditionalMarketId: 연결할 전통시장 ID. null이면 기존 연결 해제. 값 있으면 양수만 허용.
 */
public record AdminShopMarketRequest(
        @Positive Long traditionalMarketId
) {
}
