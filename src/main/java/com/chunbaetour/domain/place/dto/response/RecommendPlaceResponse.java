package com.chunbaetour.domain.place.dto.response;

import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.type.PlaceCategory;
import lombok.Builder;

import java.math.BigDecimal;

/**
 * 추천 관광지 응답 DTO (인기, 위치, 카테고리 공통 사용)
 */
@Builder
public record RecommendPlaceResponse(
        Long placeId,
        String name,
        PlaceCategory category,
        String thumbnailUrl,
        BigDecimal latitude,
        BigDecimal longitude,
        // TODO: IEEE 754 부동소수점 예외(NaN/Infinity) 방지 및 일관성을 위해 추후 float -> BigDecimal 로 타입 변경 (Cleanup 티켓)
        float rating,
        int reviewCount,
        Double distanceMeters // 주변 추천인 경우에만 값 설정 (그 외 null)
) {
    public static RecommendPlaceResponse from(Place place) {
        return RecommendPlaceResponse.builder()
                .placeId(place.getId())
                .name(place.getName())
                .category(place.getCategory())
                .thumbnailUrl(place.getThumbnailUrl())
                .latitude(place.getLat())
                .longitude(place.getLng())
                .rating(place.getRating())
                .reviewCount(place.getReviewCount())
                .build();
    }

    public static RecommendPlaceResponse fromWithDistance(Place place, double distanceMeters) {
        return RecommendPlaceResponse.builder()
                .placeId(place.getId())
                .name(place.getName())
                .category(place.getCategory())
                .thumbnailUrl(place.getThumbnailUrl())
                .latitude(place.getLat())
                .longitude(place.getLng())
                .rating(place.getRating())
                .reviewCount(place.getReviewCount())
                .distanceMeters(distanceMeters)
                .build();
    }
}
