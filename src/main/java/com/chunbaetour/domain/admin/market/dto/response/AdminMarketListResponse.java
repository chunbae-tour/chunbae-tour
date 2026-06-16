package com.chunbaetour.domain.admin.market.dto.response;

import com.chunbaetour.domain.market.entity.TraditionalMarket;

/**
 * 운영자 전통시장 목록 항목 응답 (KAN-308).
 *
 * <p>목록은 식별/검색에 필요한 요약 필드만 노출한다(상세는 {@link AdminMarketDetailResponse}).
 */
public record AdminMarketListResponse(
        Long id,
        String name,
        String marketType,
        String sido,
        String sigungu,
        String address
) {

    public static AdminMarketListResponse from(TraditionalMarket market) {
        return new AdminMarketListResponse(
                market.getId(),
                market.getName(),
                market.getMarketType(),
                market.getSido(),
                market.getSigungu(),
                market.getAddress());
    }
}
