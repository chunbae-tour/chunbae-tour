package com.chunbaetour.domain.place.dto.response;

import com.chunbaetour.domain.place.type.PlaceCategory;
import java.math.BigDecimal;

public record MapMarkerResponse(
        Long id,
        String name,
        PlaceCategory category,
        BigDecimal lat,
        BigDecimal lng,
        String thumbnailUrl
) {
    public MapMarkerResponse(Long id, String name, PlaceCategory category, Double lat, Double lng, String thumbnailUrl) {
        this(id, name, category, BigDecimal.valueOf(lat), BigDecimal.valueOf(lng), thumbnailUrl);
    }
}
