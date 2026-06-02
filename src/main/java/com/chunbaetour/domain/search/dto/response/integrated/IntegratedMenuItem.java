package com.chunbaetour.domain.search.dto.response.integrated;

import lombok.Builder;

@Builder
public record IntegratedMenuItem(
        Long id,
        String name,
        Long shopId,
        String shopName,
        Long placeId,
        String placeName,
        Integer price,
        String description,
        String thumbnailUrl
) implements IntegratedSearchItem {
    @Override
    public String getTargetType() {
        return "MENU";
    }
}
