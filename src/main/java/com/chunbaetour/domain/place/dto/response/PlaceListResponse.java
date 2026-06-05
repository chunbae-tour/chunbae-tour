package com.chunbaetour.domain.place.dto.response;

import com.chunbaetour.domain.place.type.PlaceCategory;

import java.util.List;

/**
 * 관광지 목록 조회 응답 DTO (PHASE 8-2)
 *
 * <p>커서 기반 페이지네이션 구조를 포함합니다.
 * - {@code items}: 현재 페이지의 관광지 목록.
 * - {@code hasNext}: 다음 페이지 존재 여부.
 * - {@code nextCursorId}: 다음 페이지 커서 — 마지막 아이템의 ID.
 * - {@code nextCursorRating}: 다음 페이지 커서 — 마지막 아이템의 평점.
 *
 * <p><b>[커서 설계 이유]</b><br>
 * 정렬이 {@code rating DESC, id DESC}이므로 단순 {@code id < cursorId} 조건은
 * 평점이 다른 데이터를 누락시키거나 중복 노출시키는 버그를 유발합니다.
 * 올바른 커서 조건: {@code (rating < cursorRating) OR (rating = cursorRating AND id < cursorId)}
 * 이를 위해 rating과 id를 모두 커서에 포함합니다.
 */
public record PlaceListResponse(

        /** 현재 페이지의 관광지 아이템 목록 */
        List<PlaceListItem> items,

        /** 다음 페이지 존재 여부 */
        boolean hasNext,

        /**
         * 다음 페이지 커서 — 마지막 아이템의 ID.
         * 다음 요청 시 {@code cursor} 파라미터에 전달. hasNext == false 이면 null.
         */
        Long nextCursorId,

        /**
         * 다음 페이지 커서 — 마지막 아이템의 평점.
         * 다음 요청 시 {@code cursorRating} 파라미터에 전달. hasNext == false 이면 null.
         */
        Float nextCursorRating
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
    ) {
        // QueryDSL이 Projections.constructor로 객체를 생성할 때 int 타입인 rating 값을
        // 매핑할 수 있도록 int 타입 rating을 받는 생성자를 제공합니다.
        public PlaceListItem(Long id, String name, PlaceCategory category, String address, String thumbnailUrl, int storedRating, int reviewCount) {
            this(id, name, category, address, thumbnailUrl, storedRating / 10.0f, reviewCount);
        }
    }
}
