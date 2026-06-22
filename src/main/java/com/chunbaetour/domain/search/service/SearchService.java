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
import com.chunbaetour.domain.search.dto.response.TypoCorrectedSearchResponse;
import com.chunbaetour.domain.search.type.SearchSource;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 검색 서비스.
 * <p>
 * Phase 2-2 관광지 검색, Phase 2-3 축제 검색, Phase 2-4 검색어 자동완성 기능을 담당한다.
 * 각 검색 메서드는 파라미터로 전달된 source의 종류에 따라
 * 인기 검색어 집계 여부를 결정하며, 내부적으로 {@link PopularSearchService#incrementSearchCount(String)}를
 * 호출하여 기여한다. (예: 내부 컴포넌트 호출은 집계 제외)
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
    private final SearchPlacePersonalizationService personalizationService;
    private final TypoCorrectionService typoCorrectionService;
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
     * @param track    false to skip popular search tracking.
     * @param source   검색 요청 출처 (예: community-place-selector)
     * @param userId   로그인한 유저의 PK (비로그인이면 null → 개인화 미적용)
     * @return 커서 페이지네이션이 적용된 관광지 검색 결과 (로그인 시 첫 페이지에 한해 현재 페이지 내에서 선호 카테고리 우선 노출 적용).
     *         결과 0건이고 오타 후보가 있으면 {@code didYouMean}에 교정 키워드를 포함한다.
     */
    public TypoCorrectedSearchResponse<SearchPlaceResponse> searchPlaces(String keyword, PlaceCategory category, String region, Long cursorId, int size, String clientIp, boolean track, String source, Long userId) {
        SearchSource searchSource = SearchSource.from(source);
        boolean shouldTrackSearch = track && searchSource.isTrackable();
        // 검색어 원문을 INFO 로그에 남기지 않고 존재/길이만 기록하여 운영 로그 보안 강화
        log.info("[SearchService] 관광지 검색 요청 - keywordLength: {}, category: {}, region: {}, cursorId: {}, size: {}, source: {}, track: {}",
                keyword != null ? keyword.length() : 0, category, region, cursorId, size, searchSource.name(), shouldTrackSearch);

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

        // 1. 선호 카테고리 조회 (첫 페이지 요청 & 로그인 유저인 경우에만)
        List<PlaceCategory> preferredCategories = cursorId == null
                ? personalizationService.getPreferredCategories(userId)
                : Collections.emptyList();

        // 2. 조회 (hasNext 판별을 위해 size + 1 개 조회)
        // [개인화 정렬 설계 원칙 - In-memory Boost]
        // DB 단에서 CASE WHEN 정렬과 cursor(id) 조건을 섞어 쓰면 커서 경계를 넘을 때 데이터 누락이 발생함.
        // 이를 완벽히 방지하기 위해 DB에서는 언제나 일관된 id DESC(최신순) 정렬로 데이터를 가져와 커서 무결성을 100% 보장함.
        // 그 후, 가져온 "현재 페이지 결과(resultItems)" 내부에서만 선호 카테고리를 맨 위로 끌어올리는 메모리 정렬을 수행함.
        List<SearchPlaceResponse> items = placeQueryRepository.searchByKeyword(normalized, category, region, cursorId, size);

        log.debug("[SearchService] 개인화 검색(In-memory Boost) - userId={}, preferredCategories={}", userId, preferredCategories);

        // 3. hasNext 및 nextCursor 계산
        boolean hasNext = items.size() > size;
        List<SearchPlaceResponse> resultItems = hasNext ? items.subList(0, size) : items;

        // nextCursor는 메모리 정렬 "이전"의 원본 순서(DB에서 준 순서) 기준의 마지막 ID여야만 다음 쿼리가 정확함.
        Long nextCursor = resultItems.isEmpty() ? null : resultItems.get(resultItems.size() - 1).placeId();
        String nextCursorStr = nextCursor != null ? String.valueOf(nextCursor) : null;

        // 4. In-memory Boost 정렬 적용 (현재 페이지 내부에서만 선호도 기반 재정렬)
        if (!preferredCategories.isEmpty() && !resultItems.isEmpty()) {
            List<SearchPlaceResponse> modifiableList = new ArrayList<>(resultItems);
            modifiableList.sort(Comparator.comparing((SearchPlaceResponse p) ->
                    preferredCategories.contains(p.category()) ? 0 : 1
            ).thenComparing(SearchPlaceResponse::placeId, Comparator.reverseOrder()));
            resultItems = modifiableList;
        }

        // 5. 인기 검색어 점수 집계 (유효한 키워드이고, 결과가 1건 이상 존재하며, 첫 페이지 요청일 때만)
        // 페이지네이션(cursorId != null) 시 검색 횟수가 중복으로 증가하는 어뷰징(Abuse)을 원천 차단한다.
        if (shouldTrackSearch && !resultItems.isEmpty() && cursorId == null) {
            popularSearchService.incrementSearchCount(normalized, clientIp);
        }

        // 6. 오타 교정 — 첫 페이지 + 결과 0건인 경우에만 시도
        // 페이지네이션 중(cursorId != null)에는 교정 불필요 (이미 교정된 키워드로 검색 중임)
        // 람다 캐트를 위해 effectively final 변수로 고정
        final List<SearchPlaceResponse> finalResultItems = resultItems;
        final String finalNextCursorStr = nextCursorStr;
        final boolean finalHasNext = hasNext;
        if (finalResultItems.isEmpty() && cursorId == null) {
            return typoCorrectionService.findClosestForPlaces(normalized)
                    .map(correction -> {
                        log.info("[SearchService] 관광지 오타 교정 적용 — inputLength: {}, correctionLength: {}", normalized.length(), correction.length());
                        // 교정된 키워드로 재검색 (개인화 적용은 유지)
                        List<SearchPlaceResponse> correctedItems =
                                placeQueryRepository.searchByKeyword(correction, category, region, null, size);
                        boolean correctedHasNext = correctedItems.size() > size;
                        List<SearchPlaceResponse> correctedResult = correctedHasNext
                                ? correctedItems.subList(0, size) : correctedItems;
                        Long correctedNextCursor = correctedResult.isEmpty() ? null
                                : correctedResult.get(correctedResult.size() - 1).placeId();

                        // In-memory Boost 적용
                        if (!preferredCategories.isEmpty() && !correctedResult.isEmpty()) {
                            List<SearchPlaceResponse> modifiableList = new ArrayList<>(correctedResult);
                            modifiableList.sort(Comparator.comparing((SearchPlaceResponse p) ->
                                    preferredCategories.contains(p.category()) ? 0 : 1
                            ).thenComparing(SearchPlaceResponse::placeId, Comparator.reverseOrder()));
                            correctedResult = modifiableList;
                        }

                        // 교정어 재검색 결과가 없으면 오타 제안을 하지 않고 원본 빈 결과를 반환한다.
                        if (correctedResult.isEmpty()) {
                            return TypoCorrectedSearchResponse.of(
                                    CursorPageResponse.ofAssembled(finalResultItems, finalNextCursorStr, finalHasNext, size)
                            );
                        }

                        // 오타 교정 결과가 유효하면 교정어 기준으로 인기 검색어 집계 추가
                        if (shouldTrackSearch) {
                            popularSearchService.incrementSearchCount(correction, clientIp);
                        }

                        return TypoCorrectedSearchResponse.corrected(
                                CursorPageResponse.ofAssembled(correctedResult,
                                        correctedNextCursor != null ? String.valueOf(correctedNextCursor) : null,
                                        correctedHasNext, size),
                                correction
                        );
                    })
                    .orElseGet(() -> TypoCorrectedSearchResponse.of(
                            CursorPageResponse.ofAssembled(finalResultItems, finalNextCursorStr, finalHasNext, size)));
        }

        return TypoCorrectedSearchResponse.of(
                CursorPageResponse.ofAssembled(finalResultItems, finalNextCursorStr, finalHasNext, size));
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
     * @param source    검색 요청 출처
     * @return 커서 페이지네이션이 적용된 축제 검색 결과.
     *         결과 0건이고 오타 후보가 있으면 {@code didYouMean}에 교정 키워드를 포함한다.
     */
    public TypoCorrectedSearchResponse<SearchFestivalResponse> searchFestivals(String keyword, LocalDate startDate, LocalDate endDate, String region, Long cursorId, int size, String clientIp, boolean track, String source) {
        SearchSource searchSource = SearchSource.from(source);
        boolean shouldTrackSearch = track && searchSource.isTrackable();
        log.info("[SearchService] 축제 검색 요청 - keywordLength: {}, startDate: {}, endDate: {}, region: {}, cursorId: {}, size: {}, source: {}, track: {}",
                keyword != null ? keyword.length() : 0, startDate, endDate, region, cursorId, size, searchSource.name(), shouldTrackSearch);

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

        if (shouldTrackSearch && StringUtils.hasText(normalized) && !updatedItems.isEmpty() && cursorId == null) {
            popularSearchService.incrementSearchCount(normalized, clientIp);
        }

        // 오타 교정 — 첫 페이지 + 결과 0건 + 검색어 있는 경우에만 시도
        // 람다 캐스를 위해 effectively final 변수로 고정
        final String finalNormalized = normalized;
        CursorPageResponse<SearchFestivalResponse> basePage =
                CursorPageResponse.ofAssembled(updatedItems, nextCursorStr, hasNext, size);
        if (updatedItems.isEmpty() && cursorId == null && StringUtils.hasText(finalNormalized)) {
            return typoCorrectionService.findClosestForFestivals(finalNormalized)
                    .map(correction -> {
                        log.info("[SearchService] 축제 오타 교정 적용 — inputLength: {}, correctionLength: {}", finalNormalized.length(), correction.length());
                        List<Festival> correctedFestivals =
                                festivalQueryRepository.searchFestivals(correction, startDate, endDate, region, null, size);
                        boolean correctedHasNext = correctedFestivals.size() > size;
                        List<Festival> correctedResult = correctedHasNext
                                ? correctedFestivals.subList(0, size) : correctedFestivals;
                        Long correctedNextCursor = correctedResult.isEmpty() ? null
                                : correctedResult.get(correctedResult.size() - 1).getId();
                        List<SearchFestivalResponse> correctedItems = correctedResult.stream()
                                .map(item -> SearchFestivalResponse.from(item,
                                        FestivalProgressStatus.of(item.getStartDate(), item.getEndDate(), today)))
                                .toList();
                        // 교정어 재검색 결과가 없으면 오타 제안을 하지 않고 원본 빈 결과를 반환한다.
                        if (correctedResult.isEmpty()) {
                            return TypoCorrectedSearchResponse.of(basePage);
                        }

                        // 오타 교정 결과가 유효하면 교정어 기준으로 인기 검색어 집계 추가
                        if (shouldTrackSearch) {
                            popularSearchService.incrementSearchCount(correction, clientIp);
                        }

                        return TypoCorrectedSearchResponse.corrected(
                                CursorPageResponse.ofAssembled(correctedItems,
                                        correctedNextCursor != null ? String.valueOf(correctedNextCursor) : null,
                                        correctedHasNext, size),
                                correction
                        );
                    })
                    .orElseGet(() -> TypoCorrectedSearchResponse.of(basePage));
        }
        return TypoCorrectedSearchResponse.of(basePage);
    }

    /**
     * 축제 검색 v1 — 구 키(location, thumbnailUrl) 응답.
     * <p>
     * {@code GET /api/v1/search/festivals} 하위 호환용. 쿼리 로직은 v2와 동일하며 오타 교정도 동일하게 적용된다.
     * </p>
     */
    public TypoCorrectedSearchResponse<SearchFestivalV1Response> searchFestivalsV1(String keyword, LocalDate startDate, LocalDate endDate, String region, Long cursorId, int size, String clientIp, boolean track, String source) {
        SearchSource searchSource = SearchSource.from(source);
        boolean shouldTrackSearch = track && searchSource.isTrackable();
        log.info("[SearchService] 축제 검색 v1 요청 - keywordLength: {}, startDate: {}, endDate: {}, region: {}, cursorId: {}, size: {}, source: {}, track: {}",
                keyword != null ? keyword.length() : 0, startDate, endDate, region, cursorId, size, searchSource.name(), shouldTrackSearch);

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

        if (shouldTrackSearch && StringUtils.hasText(normalized) && !v1Items.isEmpty() && cursorId == null) {
            popularSearchService.incrementSearchCount(normalized, clientIp);
        }

        // 오타 교정 — 첫 페이지 + 결과 0건 + 검색어 있는 경우에만 시도
        // 람다 캐스를 위해 effectively final 변수로 고정
        final String finalNormalizedV1 = normalized;
        CursorPageResponse<SearchFestivalV1Response> basePage =
                CursorPageResponse.ofAssembled(v1Items, nextCursorStr, hasNext, size);
        if (v1Items.isEmpty() && cursorId == null && StringUtils.hasText(finalNormalizedV1)) {
            return typoCorrectionService.findClosestForFestivals(finalNormalizedV1)
                    .map(correction -> {
                        log.info("[SearchService] 축제(v1) 오타 교정 적용 — inputLength: {}, correctionLength: {}", finalNormalizedV1.length(), correction.length());
                        List<Festival> correctedFestivals =
                                festivalQueryRepository.searchFestivals(correction, startDate, endDate, region, null, size);
                        boolean correctedHasNext = correctedFestivals.size() > size;
                        List<Festival> correctedResult = correctedHasNext
                                ? correctedFestivals.subList(0, size) : correctedFestivals;
                        Long correctedNextCursor = correctedResult.isEmpty() ? null
                                : correctedResult.get(correctedResult.size() - 1).getId();
                        LocalDate correctionToday = LocalDate.now(clock);
                        List<SearchFestivalV1Response> correctedItems = correctedResult.stream()
                                .map(item -> SearchFestivalV1Response.from(item,
                                        FestivalProgressStatus.of(item.getStartDate(), item.getEndDate(), correctionToday)))
                                .toList();
                        // 교정어 재검색 결과가 없으면 오타 제안을 하지 않고 원본 빈 결과를 반환한다.
                        if (correctedResult.isEmpty()) {
                            return TypoCorrectedSearchResponse.of(basePage);
                        }

                        // 오타 교정 결과가 유효하면 교정어 기준으로 인기 검색어 집계 추가
                        if (shouldTrackSearch) {
                            popularSearchService.incrementSearchCount(correction, clientIp);
                        }

                        return TypoCorrectedSearchResponse.corrected(
                                CursorPageResponse.ofAssembled(correctedItems,
                                        correctedNextCursor != null ? String.valueOf(correctedNextCursor) : null,
                                        correctedHasNext, size),
                                correction
                        );
                    })
                    .orElseGet(() -> TypoCorrectedSearchResponse.of(basePage));
        }
        return TypoCorrectedSearchResponse.of(basePage);
    }

}
