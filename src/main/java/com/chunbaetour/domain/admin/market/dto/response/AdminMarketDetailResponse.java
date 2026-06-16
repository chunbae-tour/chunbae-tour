package com.chunbaetour.domain.admin.market.dto.response;

import com.chunbaetour.domain.market.entity.TraditionalMarket;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 운영자 전통시장 단건 상세 응답 (KAN-308).
 *
 * <p>공공데이터 sync가 원천이라 조회 전용 — 전체 필드 노출. 수정/숨김/삭제는 원천관리 정책(B13) 확정 후 별도.
 */
public record AdminMarketDetailResponse(
        Long id,
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
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static AdminMarketDetailResponse from(TraditionalMarket market) {
        return new AdminMarketDetailResponse(
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
                market.getCreatedAt(),
                market.getUpdatedAt());
    }
}
