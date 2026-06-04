package com.chunbaetour.domain.place.dto.request;

import com.chunbaetour.domain.place.type.PlaceCategory;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 관광지 목록 조회 요청 DTO (PHASE 8-2)
 *
 * <p>커서 기반 페이지네이션을 지원하며, 카테고리와 지역으로 필터링 가능합니다.
 *
 * <p><b>[복합 커서 설계]</b><br>
 * 정렬이 {@code rating DESC, id DESC}이므로 단순 id 커서만으로는 페이지 경계에서
 * 데이터 누락/중복이 발생합니다. 이전 응답의 {@code nextCursorId}와
 * {@code nextCursorRating} 두 값을 함께 전달해야 정확한 페이징이 보장됩니다.
 *
 * <p>파라미터 상세:
 * <ul>
 *   <li>{@code category} - PlaceCategory enum 값 (선택). null이면 전체 카테고리.</li>
 *   <li>{@code region} - 주소에 포함될 문자열 (선택). 예: "서귀포". null이면 전체 지역.</li>
 *   <li>{@code cursor} - 이전 응답의 {@code nextCursorId} 값 (선택). {@code cursorRating}과 쌍으로 전달.</li>
 *   <li>{@code cursorRating} - 이전 응답의 {@code nextCursorRating} 값 (선택). {@code cursor}와 쌍으로 전달.</li>
 *   <li>{@code size} - 한 페이지 최대 건수. 기본값 20, 최대 50.</li>
 * </ul>
 */
public record PlaceListRequest(

        /** 카테고리 필터 (선택). null이면 전체 카테고리 반환. */
        PlaceCategory category,

        /** 지역 필터 (선택). 주소에 해당 문자열이 포함된 관광지만 반환. */
        String region,

        /**
         * 커서 ID — 이전 응답의 nextCursorId 값 (선택).
         * cursorRating과 반드시 쌍으로 전달해야 합니다.
         */
        Long cursor,

        /**
         * 커서 평점 — 이전 응답의 nextCursorRating 값 (선택).
         * cursor와 반드시 쌍으로 전달해야 합니다.
         */
        Float cursorRating,

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

    /**
     * 커서 쌍 유효성 검증 — cursor와 cursorRating은 반드시 함께 전달되거나 함께 비워야 합니다.
     * 하나만 보내면 잘못된 페이징 결과가 반환됩니다.
     */
    @JsonIgnore
    @AssertTrue(message = "cursor와 cursorRating은 함께 전달되어야 합니다.")
    public boolean isCursorPairValid() {
        return (cursor == null) == (cursorRating == null);
    }
}
