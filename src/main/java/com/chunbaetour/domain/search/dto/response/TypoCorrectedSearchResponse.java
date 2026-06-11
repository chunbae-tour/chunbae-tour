package com.chunbaetour.domain.search.dto.response;

import com.chunbaetour.domain.common.response.CursorPageResponse;
import java.util.List;

/**
 * 오타 교정 검색 응답 래퍼 (KAN-276).
 *
 * <p>검색 결과({@link CursorPageResponse})와 오타 교정 제안({@code didYouMean})을 함께 반환한다.
 *
 * <p><b>didYouMean 의미:</b>
 * <ul>
 *   <li>{@code null} — 오타 교정 없음 (정상 검색 또는 교정 후보 없음)</li>
 *   <li>non-null 문자열 — 원본 검색어 대신 이 단어로 교정하여 검색한 결과임을 클라이언트에 알림.
 *       예: 사용자가 "경복굿" 검색 시 → {@code didYouMean = "경복궁"}</li>
 * </ul>
 *
 * @param <T>        검색 결과 아이템 타입 (SearchPlaceResponse, SearchFestivalResponse 등)
 * @param result     커서 페이지네이션이 적용된 검색 결과
 * @param didYouMean 오타 교정 제안 키워드. null이면 교정 없음
 */
public record TypoCorrectedSearchResponse<T>(
        List<T> content,
        String nextCursor,
        boolean hasNext,
        int size,
        String didYouMean
) {

    /**
     * 오타 교정 없는 정상 결과를 생성한다.
     *
     * @param result 검색 결과
     * @return didYouMean = null인 응답
     */
    public static <T> TypoCorrectedSearchResponse<T> of(CursorPageResponse<T> result) {
        return new TypoCorrectedSearchResponse<>(
                result.content(),
                result.nextCursor(),
                result.hasNext(),
                result.size(),
                null
        );
    }

    /**
     * 오타 교정이 적용된 결과를 생성한다.
     *
     * @param result     교정된 키워드로 재검색한 결과
     * @param correction 교정된 키워드
     * @return didYouMean = correction인 응답
     */
    public static <T> TypoCorrectedSearchResponse<T> corrected(CursorPageResponse<T> result, String correction) {
        return new TypoCorrectedSearchResponse<>(
                result.content(),
                result.nextCursor(),
                result.hasNext(),
                result.size(),
                correction
        );
    }
}
