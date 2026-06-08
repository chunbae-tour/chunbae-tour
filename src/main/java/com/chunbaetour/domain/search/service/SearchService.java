package com.chunbaetour.domain.search.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.festival.entity.Festival;
import com.chunbaetour.domain.festival.repository.FestivalQueryRepository;
import com.chunbaetour.domain.festival.type.FestivalProgressStatus;
import com.chunbaetour.domain.place.repository.PlaceQueryRepository;
import com.chunbaetour.domain.place.type.PlaceCategory;
import com.chunbaetour.domain.search.constant.SearchRedisKeys;
import com.chunbaetour.domain.search.dto.response.SearchFestivalResponse;
import com.chunbaetour.domain.search.dto.response.SearchFestivalV1Response;
import com.chunbaetour.domain.search.dto.response.SearchPlaceResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 검색 서비스.
 * <p>
 * Phase 2-2 관광지 검색, Phase 2-3 축제 검색, Phase 2-4 검색어 자동완성 기능을 담당한다.
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
    private final FestivalQueryRepository festivalQueryRepository;
    private final PopularSearchService popularSearchService;
    private final StringRedisTemplate stringRedisTemplate;
    private final Clock clock;

    // ──────────────────────────────────────────────────────────────────────────
    // 상수
    // ──────────────────────────────────────────────────────────────────────────





    // ──────────────────────────────────────────────────────────────────────────
    // 관광지 검색 (Phase 2-2)
    // ──────────────────────────────────────────────────────────────────────────

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
     * @param clientIp 클라이언트 IP 주소
     * @return 커서 페이지네이션이 적용된 관광지 검색 결과
     */
    public CursorPageResponse<SearchPlaceResponse> searchPlaces(String keyword, PlaceCategory category, String region, Long cursorId, int size, String clientIp) {
        // 검색어 원문을 INFO 로그에 남기지 않고 존재/길이만 기록하여 운영 로그 보안 강화
        log.info("[SearchService] 관광지 검색 요청 - keywordLength: {}, category: {}, region: {}, cursorId: {}, size: {}",
                keyword != null ? keyword.length() : 0, category, region, cursorId, size);

        // 검색어 양끝 공백 제거 (정규화) - 이후 로직 전체에 적용
        String normalized = keyword != null ? keyword.strip() : null;

        // 검색어 필수 검증 (PLACE_005)
        if (!StringUtils.hasText(normalized)) {
            throw new BusinessException(ErrorCode.SEARCH_KEYWORD_TOO_SHORT);
        }

        // 정책적 예외 처리: 검색어 길이 제한 (최대 50자) (PLACE_006)
        if (normalized.length() > 50) {
            throw new BusinessException(ErrorCode.SEARCH_KEYWORD_TOO_LONG);
        }

        // 1. 조회 (hasNext 판별을 위해 size + 1 개 조회)
        List<SearchPlaceResponse> items = placeQueryRepository.searchByKeyword(normalized, category, region, cursorId, size);

        // 2. hasNext 및 nextCursor 계산
        boolean hasNext = items.size() > size;
        List<SearchPlaceResponse> resultItems = hasNext ? items.subList(0, size) : items;

        Long nextCursor = resultItems.isEmpty() ? null : resultItems.get(resultItems.size() - 1).placeId();
        String nextCursorStr = nextCursor != null ? String.valueOf(nextCursor) : null;

        // 3. 인기 검색어 점수 집계 (유효한 키워드이고, 결과가 1건 이상 존재하며, 첫 페이지 요청일 때만)
        // 페이지네이션(cursorId != null) 시 검색 횟수가 중복으로 증가하는 어뷰징(Abuse)을 원천 차단한다.
        if (!resultItems.isEmpty() && cursorId == null) {
            popularSearchService.incrementSearchCount(normalized, clientIp);
        }

        return new CursorPageResponse<>(resultItems, nextCursorStr, hasNext, resultItems.size());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 축제 검색 (Phase 2-3)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * 축제 검색 (Phase 2-3).
     * <p>
     * SA 명세서: {@code GET /api/v1/search/festivals}
     * QueryDSL을 통해 조건 기반 축제 정보를 커서 기반으로 조회한다.
     * 유효한 검색어가 입력된 경우 인기 검색어 점수를 증가시킨다.
     * </p>
     *
     * @param keyword   검색어 (옵션)
     * @param startDate 시작일 필터 (옵션)
     * @param endDate   종료일 필터 (옵션)
     * @param region    지역 필터 (옵션)
     * @param cursorId  커서용 마지막 festivalId
     * @param size      페이지 사이즈
     * @param clientIp  클라이언트 IP 주소
     * @return 커서 페이지네이션이 적용된 축제 검색 결과
     */
    public CursorPageResponse<SearchFestivalResponse> searchFestivals(String keyword, LocalDate startDate, LocalDate endDate, String region, Long cursorId, int size, String clientIp) {
        log.info("[SearchService] 축제 검색 요청 - keywordLength: {}, startDate: {}, endDate: {}, region: {}, cursorId: {}, size: {}",
                keyword != null ? keyword.length() : 0, startDate, endDate, region, cursorId, size);

        // 검색 시작일/종료일 유효성 선검증
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessException(ErrorCode.SEARCH_INVALID_DATE_RANGE);
        }

        String normalized = keyword;
        // 검색어 길이 제한 (최대 50자) (PLACE_006)
        if (keyword != null) {
            normalized = keyword.strip();
            if (StringUtils.hasText(normalized) && normalized.length() > 50) {
                throw new BusinessException(ErrorCode.SEARCH_KEYWORD_TOO_LONG);
            }
            // 리뷰 반영: 공백만 있는 경우 null 처리하여 DB 쿼리 전송 방지
            normalized = StringUtils.hasText(normalized) ? normalized : null;
        }

        // 1. 조회 (hasNext 판별을 위해 size + 1 개 조회)
        List<Festival> items = festivalQueryRepository.searchFestivals(normalized, startDate, endDate, region, cursorId, size);

        // 2. hasNext 및 nextCursor 계산
        boolean hasNext = items.size() > size;
        List<Festival> resultItems = hasNext ? items.subList(0, size) : items;

        Long nextCursor = resultItems.isEmpty() ? null : resultItems.get(resultItems.size() - 1).getId();
        String nextCursorStr = nextCursor != null ? String.valueOf(nextCursor) : null;

        // 3. 엔티티 -> DTO 변환 및 progressStatus 동적 계산
        LocalDate today = LocalDate.now(clock);
        List<SearchFestivalResponse> updatedItems = resultItems.stream()
                .map(item -> SearchFestivalResponse.from(item, FestivalProgressStatus.of(item.getStartDate(), item.getEndDate(), today)))
                .toList();

        if (StringUtils.hasText(normalized) && !updatedItems.isEmpty() && cursorId == null) {
            popularSearchService.incrementSearchCount(normalized, clientIp);
        }

        return new CursorPageResponse<>(updatedItems, nextCursorStr, hasNext, updatedItems.size());
    }

    /**
     * 축제 검색 v1 — 구 키(location, thumbnailUrl) 응답.
     * <p>
     * {@code GET /api/v1/search/festivals} 하위 호환용. 쿼리 로직은 v2와 동일.
     * </p>
     */
    public CursorPageResponse<SearchFestivalV1Response> searchFestivalsV1(String keyword, LocalDate startDate, LocalDate endDate, String region, Long cursorId, int size, String clientIp) {
        log.info("[SearchService] 축제 검색 v1 요청 - keywordLength: {}, startDate: {}, endDate: {}, region: {}, cursorId: {}, size: {}",
                keyword != null ? keyword.length() : 0, startDate, endDate, region, cursorId, size);

        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessException(ErrorCode.SEARCH_INVALID_DATE_RANGE);
        }

        String normalized = keyword;
        if (keyword != null) {
            normalized = keyword.strip();
            if (StringUtils.hasText(normalized) && normalized.length() > 50) {
                throw new BusinessException(ErrorCode.SEARCH_KEYWORD_TOO_LONG);
            }
            normalized = StringUtils.hasText(normalized) ? normalized : null;
        }

        List<Festival> items = festivalQueryRepository.searchFestivals(normalized, startDate, endDate, region, cursorId, size);

        boolean hasNext = items.size() > size;
        List<Festival> resultItems = hasNext ? items.subList(0, size) : items;

        Long nextCursor = resultItems.isEmpty() ? null : resultItems.get(resultItems.size() - 1).getId();
        String nextCursorStr = nextCursor != null ? String.valueOf(nextCursor) : null;

        LocalDate today = LocalDate.now(clock);
        List<SearchFestivalV1Response> v1Items = resultItems.stream()
                .map(item -> SearchFestivalV1Response.from(item, FestivalProgressStatus.of(item.getStartDate(), item.getEndDate(), today)))
                .toList();

        if (StringUtils.hasText(normalized) && !v1Items.isEmpty() && cursorId == null) {
            popularSearchService.incrementSearchCount(normalized, clientIp);
        }

        return new CursorPageResponse<>(v1Items, nextCursorStr, hasNext, v1Items.size());
    }

}
