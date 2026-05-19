package com.chunbaetour.domain.place.dto.response;

import com.chunbaetour.domain.place.type.PlaceCategory;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NearbyPlaceResponse {
    
    private Long placeId;
    private String name;
    private PlaceCategory category;
    private String imageUrl;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private float rating;
    private int reviewCount;
    private double distanceMeters;
    
    @Builder
    public NearbyPlaceResponse(Long placeId, String name, PlaceCategory category, String imageUrl, 
                               BigDecimal latitude, BigDecimal longitude, float rating, 
                               int reviewCount, double distanceMeters) {
        this.placeId = placeId;
        this.name = name;
        this.category = category;
        this.imageUrl = imageUrl;
        this.latitude = latitude;
        this.longitude = longitude;
        this.rating = rating;
        this.reviewCount = reviewCount;
        this.distanceMeters = distanceMeters;
    }
}

