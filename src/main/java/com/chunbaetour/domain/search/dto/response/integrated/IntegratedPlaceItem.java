package com.chunbaetour.domain.search.dto.response.integrated;

import lombok.Builder;
import java.util.List;

@Builder
public record IntegratedPlaceItem(
        Long id,
        String name,
        String category,
        String address,
        String thumbnailUrl,
        int matchedShopCount,
        List<String> matchedMenuNames
) implements IntegratedSearchItem {
    @Override
    public String getTargetType() {
        return "PLACE";
    }
}
