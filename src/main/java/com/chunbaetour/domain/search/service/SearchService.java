package com.chunbaetour.domain.search.service;

import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.place.repository.PlaceQueryRepository;
import com.chunbaetour.domain.place.type.PlaceCategory;
import com.chunbaetour.domain.search.dto.response.SearchPlaceResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 검색 서비스.
 * <p>
 * Phase 2-2 관광지 검색, Phase 2-3 축제 검색 등의 검색 기능을 담당한다.
 * 각 검색 메서드는 내부적으로 {@link PopularSearchService#incrementSearchCount(String)}를
 * 호출하여 인기 검색어 집계에 기여한다.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchService {

    private final PlaceQueryRepository placeQueryRepository;
    private final PopularSearchService popularSearchService;

    /**
     * 관광지 키워드 검색 (Phase 2-2).
     * <p>
     * SA 명세서: {@code GET /api/v1/search/places}
     * QueryDSL을 통해 키워드 기반 관광지 정보를 커서 기반으로 조회한다.
     * 유효한 검색어가 입력된 경우 인기 검색어 점수를 증가시킨다.
     * </p>
     *
     * @param keyword  검색어 (옵션)
     * @param category 카테고리 필터 (옵션)
     * @param region   지역 필터 (옵션)
     * @param cursorId 커서용 마지막 placeId
     * @param size     페이지 사이즈
     * @return 커서 페이지네이션이 적용된 관광지 검색 결과
     */
    public CursorPageResponse<SearchPlaceResponse> searchPlaces(String keyword, PlaceCategory category, String region, Long cursorId, int size) {
        // [CodeRabbit 리뷰 반영] 검색어 원문을 INFO 로그에 남기지 않고 존재/길이만 기록하여 운영 로그 보안 강화
        log.info("[SearchService] 관광지 검색 요청 - keywordLength: {}, category: {}, region: {}, cursorId: {}, size: {}",
                keyword != null ? keyword.length() : 0, category, region, cursorId, size);

        // [실무 수준 리뷰 반영] 검색어 필수 검증 (PLACE_005)
        // 검색어가 없으면 전체 테이블을 풀스캔하여 부하를 일으키므로 원천 차단해야 함.
        if (!StringUtils.hasText(keyword)) {
            throw new com.chunbaetour.domain.common.error.BusinessException(
                    com.chunbaetour.domain.common.error.ErrorCode.SEARCH_KEYWORD_TOO_SHORT);
        }

        // [15년차 리뷰 반영] 정책적 예외 처리: 검색어 길이 제한 (최대 50자) (PLACE_006)
        if (keyword.length() > 50) {
            throw new com.chunbaetour.domain.common.error.BusinessException(
                    com.chunbaetour.domain.common.error.ErrorCode.SEARCH_KEYWORD_TOO_LONG);
        }

        // 1. 조회 (hasNext 판별을 위해 size + 1 개 조회)
        List<SearchPlaceResponse> items = placeQueryRepository.searchByKeyword(keyword, category, region, cursorId, size);

        // 2. hasNext 및 nextCursor 계산
        boolean hasNext = items.size() > size;
        if (hasNext) {
            items.remove(size);
        }

        Long nextCursor = items.isEmpty() ? null : items.get(items.size() - 1).placeId();
        String nextCursorStr = nextCursor != null ? String.valueOf(nextCursor) : null;

        // 3. 인기 검색어 점수 집계 (유효한 키워드이고, 결과가 1건 이상 존재하며, 첫 페이지 요청일 때만)
        // [15년차 리뷰 반영] 페이지네이션(cursorId != null) 시 검색 횟수가 중복으로 증가하는 어뷰징(Abuse)을 원천 차단한다.
        if (!items.isEmpty() && cursorId == null) {
            popularSearchService.incrementSearchCount(keyword);
        }

        return new CursorPageResponse<>(items, nextCursorStr, hasNext, items.size());
    }
}
