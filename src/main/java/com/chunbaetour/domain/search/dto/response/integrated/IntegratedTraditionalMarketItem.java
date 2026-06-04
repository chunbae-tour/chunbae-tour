package com.chunbaetour.domain.search.dto.response.integrated;

import lombok.Builder;

@Builder
public record IntegratedTraditionalMarketItem(
        Long id,
        String name,
        String marketType,
        String address,
        String phoneNumber,
        String homepageUrl,
        Integer establishYear
) implements IntegratedSearchItem {
    @Override
    public String getTargetType() {
        return "TRADITIONAL_MARKET";
    }
}
