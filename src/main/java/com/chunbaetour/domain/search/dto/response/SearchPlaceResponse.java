package com.chunbaetour.domain.search.dto.response;

import com.chunbaetour.domain.place.type.PlaceCategory;

/**
 * 관광지 검색 결과 단건 응답 DTO.
 * <p>
 * Phase 2-2: {@code GET /api/v1/search/places} 의 결과 아이템.
 * </p>
 *
 * @param placeId  관광지 PK
 * @param name     관광지 이름
 * @param category 카테고리 (예: TOURIST_SPOT)
 * @param address  주소
 * @param imageUrl 썸네일/이미지 URL
 * @param rating   평균 별점
 * @param reviewCount 리뷰 수
 */
public record SearchPlaceResponse(
        Long placeId,
        String name,
        PlaceCategory category,
        String address,
        String imageUrl,
        float rating,
        int reviewCount
) {
}
