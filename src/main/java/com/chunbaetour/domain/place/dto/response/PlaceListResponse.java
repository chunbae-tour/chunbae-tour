package com.chunbaetour.domain.place.dto.response;

import com.chunbaetour.domain.place.type.PlaceCategory;

import java.util.List;

/**
 * 관광지 목록 조회 응답 DTO (PHASE 8-2)
 *
 * <p>커서 기반 페이지네이션 구조를 포함합니다.
 * - {@code items}: 현재 페이지의 관광지 목록.
 * - {@code hasNext}: 다음 페이지 존재 여부.
 * - {@code nextCursor}: 다음 페이지 조회 시 cursor 파라미터에 전달할 값.
 *   hasNext가 false이면 null을 반환합니다.
 */
public record PlaceListResponse(

        /** 현재 페이지의 관광지 아이템 목록 */
        List<PlaceListItem> items,

        /** 다음 페이지 존재 여부 */
        boolean hasNext,

        /**
         * 다음 페이지 요청 시 사용할 커서 (마지막 아이템의 ID).
         * hasNext == false 이면 null.
         */
        Long nextCursor
) {

    /**
     * 관광지 목록 응답의 개별 아이템.
     *
     * <p>상세 조회({@code GET /api/v1/places/{placeId}})에 비해
     * 목록에서 필요한 핵심 필드만 포함하여 응답 크기를 최소화합니다.
     */
    public record PlaceListItem(
            /** 관광지 ID */
            Long id,

            /** 관광지 이름 */
            String name,

            /** 카테고리 (TOURIST_SPOT, TRADITIONAL_MARKET) */
            PlaceCategory category,

            /** 주소 */
            String address,

            /** 대표 이미지 URL */
            String thumbnailUrl,

            /** 평균 별점 (0.0 ~ 5.0) */
            float rating,

            /** 리뷰 개수 */
            int reviewCount
    ) {}
}
