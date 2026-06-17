package com.chunbaetour.domain.market.dto.response;

import com.chunbaetour.domain.like.type.LikeTargetType;
import com.chunbaetour.domain.market.entity.TraditionalMarket;
import java.math.BigDecimal;

/**
 * Public traditional market detail response.
 *
 * <p>Used by my-page liked MARKET cards and public market detail screens. Admin-only audit fields
 * such as createdAt/updatedAt stay in the admin DTO, while this DTO exposes customer-facing market
 * information and the current user's liked state.
 */
public record TraditionalMarketDetailResponse(
        Long marketId,
        String name,
        String address,
        BigDecimal lat,
        BigDecimal lng,
        String marketType,
        String phoneNumber,
        String homepageUrl,
        Integer establishYear,
        String sido,
        String sigungu,
        String region,
        String targetType,
        boolean isLiked
) {

    public static TraditionalMarketDetailResponse of(TraditionalMarket market, boolean isLiked) {
        return new TraditionalMarketDetailResponse(
                market.getId(),
                market.getName(),
                market.getAddress(),
                market.getLat(),
                market.getLng(),
                market.getMarketType(),
                market.getPhoneNumber(),
                market.getHomepageUrl(),
                market.getEstablishYear(),
                market.getSido(),
                market.getSigungu(),
                region(market.getSido(), market.getSigungu()),
                LikeTargetType.MARKET.name(),
                isLiked
        );
    }

    private static String region(String sido, String sigungu) {
        if (sido == null || sido.isBlank()) {
            // sido·sigungu 모두 비어도 null이 아닌 "" 반환 — 클라이언트 null 처리 부담 제거 (coderabbit #601).
            return (sigungu == null || sigungu.isBlank()) ? "" : sigungu;
        }
        if (sigungu == null || sigungu.isBlank()) {
            return sido;
        }
        return sido + " " + sigungu;
    }
}
