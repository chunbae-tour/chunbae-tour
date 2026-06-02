package com.chunbaetour.domain.search.dto.response.integrated;

import lombok.Builder;
import java.util.List;

@Builder
public record IntegratedShopItem(
        Long id,
        Long shopId,
        String name,
        Long placeId,
        String placeName,
        String category,
        String address,
        String thumbnailUrl,
        Double rating,
        Integer reviewCount,
        List<String> matchedMenuNames
) implements IntegratedSearchItem {
    @Override
    public String getTargetType() {
        return "SHOP";
    }
}
