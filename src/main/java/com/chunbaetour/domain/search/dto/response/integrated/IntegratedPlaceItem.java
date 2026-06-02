package com.chunbaetour.domain.search.dto.response.integrated;

import com.chunbaetour.domain.place.type.PlaceCategory;
import lombok.Builder;
import java.util.List;

@Builder
public record IntegratedPlaceItem(
        Long id,
        String name,
        PlaceCategory category,
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
