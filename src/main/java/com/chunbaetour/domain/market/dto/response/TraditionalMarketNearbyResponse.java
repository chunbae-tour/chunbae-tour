package com.chunbaetour.domain.market.dto.response;

import java.math.BigDecimal;
import lombok.Builder;

/**
 * 전통시장 위치 기반 조회 응답 DTO.
 * Place와 공통 필드로 프론트 통합 카드 렌더링 지원.
 */
@Builder
public record TraditionalMarketNearbyResponse(
        Long id,
        String name,
        String address,
        BigDecimal lat,
        BigDecimal lng,
        String thumbnailUrl,
        String marketType,
        String targetType
) {
    public TraditionalMarketNearbyResponse {
        if (targetType == null) {
            targetType = "TRADITIONAL_MARKET";
        }
    }
}
