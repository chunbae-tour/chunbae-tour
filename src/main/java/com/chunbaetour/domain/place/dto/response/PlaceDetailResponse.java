package com.chunbaetour.domain.place.dto.response;

import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.type.PlaceCategory;

import java.math.BigDecimal;
import java.util.List;

/**
 * 관광지 상세 조회 응답 DTO
 * GET /api/v1/places/{placeId}
 * - isLiked: 비로그인 시 false 고정 (PHASE 3에서 로그인 사용자용 true 처리 예정)
 */
public record PlaceDetailResponse(
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
        int likeCount,
        boolean isLiked
) {

    /**
     * Place 엔티티와 imageUrls 파싱 결과, 찜 여부를 받아 DTO 생성
     *
     * @param place      Place 엔티티
     * @param imageUrls  JSON 파싱된 이미지 URL 목록
     * @param isLiked    로그인 사용자의 찜 여부 (비로그인 시 false)
     */
    public static PlaceDetailResponse of(Place place, List<String> imageUrls, boolean isLiked) {
        return new PlaceDetailResponse(
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
                place.getLikeCount(),
                isLiked
        );
    }

    /**
     * 캐시된 DTO와 찜 여부를 받아 응답 DTO 생성
     */
    public static PlaceDetailResponse of(PlaceCacheDto cacheDto, boolean isLiked) {
        return new PlaceDetailResponse(
                cacheDto.placeId(),
                cacheDto.name(),
                cacheDto.description(),
                cacheDto.category(),
                cacheDto.address(),
                cacheDto.latitude(),
                cacheDto.longitude(),
                cacheDto.imageUrls(),
                cacheDto.operatingHours(),
                cacheDto.closedDays(),
                cacheDto.admissionFee(),
                cacheDto.phone(),
                cacheDto.rating(),
                cacheDto.reviewCount(),
                cacheDto.likeCount(),
                isLiked
        );
    }
}
