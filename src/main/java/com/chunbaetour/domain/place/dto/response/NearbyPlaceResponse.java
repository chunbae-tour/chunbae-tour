package com.chunbaetour.domain.place.dto.response;

import com.chunbaetour.domain.place.type.PlaceCategory;

import java.math.BigDecimal;

public record NearbyPlaceResponse(
    Long placeId,
    String name,
    PlaceCategory category,
    String imageUrl,
    BigDecimal latitude,
    BigDecimal longitude,
    float rating,
    int reviewCount,
    double distanceMeters
) {}

