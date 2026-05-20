package com.chunbaetour.domain.place.dto.response;

import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.type.PlaceCategory;

import java.math.BigDecimal;
import java.util.List;

/**
 * 관광지 상세 조회 캐시용 DTO
 * API 응답용인 PlaceDetailResponse 와 분리하여
 * 사용자 개인화 데이터(isLiked 등)가 공통 캐시에 오염되지 않도록 방지
 */
public record PlaceCacheDto(
        Long placeId,
        String name,
        String description,
        PlaceCategory category,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        List<String> imageUrls,
        String operatingHours,
        String closedDays,
        String admissionFee,
        String phone,
        float rating,
        int reviewCount,
        int likeCount
) {
    public static PlaceCacheDto of(Place place, List<String> imageUrls) {
        return new PlaceCacheDto(
                place.getId(),
                place.getName(),
                place.getDescription(),
                place.getCategory(),
                place.getAddress(),
                place.getLat(),
                place.getLng(),
                imageUrls,
                place.getOperatingHours(),
                place.getClosedDays(),
                place.getAdmissionFee(),
                place.getPhone(),
                place.getRating(),
                place.getReviewCount(),
                place.getLikeCount()
        );
    }
}
