package com.chunbaetour.domain.place.dto.request;

import com.chunbaetour.domain.place.type.PlaceCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 관광지 목록 조회 요청 DTO (PHASE 8-2)
 *
 * <p>커서 기반 페이지네이션을 지원하며, 카테고리와 지역으로 필터링 가능합니다.
 * 커서는 마지막으로 받은 관광지의 ID 값이며, 정렬 기준은 평점 내림차순 + ID 내림차순입니다.
 *
 * <p>파라미터 상세:
 * <ul>
 *   <li>{@code category} - PlaceCategory enum 값 (선택). null이면 전체 카테고리 조회.</li>
 *   <li>{@code region} - 주소에 포함될 문자열 (선택). 예: "서귀포", "제주시". null이면 전체 지역 조회.</li>
 *   <li>{@code cursor} - 이전 응답의 {@code nextCursor} 값 (선택). null이면 첫 페이지 조회.</li>
 *   <li>{@code size} - 한 페이지에 반환할 최대 건수. 기본값 20, 최대 50.</li>
 * </ul>
 */
public record PlaceListRequest(

        /** 카테고리 필터 (선택). null이면 전체 카테고리 반환. */
        PlaceCategory category,

        /** 지역 필터 (선택). 주소에 해당 문자열이 포함된 관광지만 반환. */
        String region,

        /**
         * 커서 — 이전 응답의 nextCursor 값 (선택).
         * 평점 내림차순 정렬 시 동률이 많을 수 있으므로 ID를 2차 정렬 키로 사용.
         * 커서가 없으면 첫 번째 페이지를 의미.
         */
        Long cursor,

        @Min(value = 1, message = "size는 1 이상이어야 합니다.")
        @Max(value = 50, message = "size는 50 이하이어야 합니다.")
        Integer size
) {
    /** 기본값 처리: size가 null이면 20으로 설정 */
    public PlaceListRequest {
        if (size == null) {
            size = 20;
        }
    }
}
