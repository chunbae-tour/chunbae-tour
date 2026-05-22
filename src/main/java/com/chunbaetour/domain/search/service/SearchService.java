package com.chunbaetour.domain.search.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.festival.entity.Festival;
import com.chunbaetour.domain.festival.repository.FestivalQueryRepository;
import com.chunbaetour.domain.festival.type.FestivalProgressStatus;
import com.chunbaetour.domain.place.repository.PlaceQueryRepository;
import com.chunbaetour.domain.place.type.PlaceCategory;
import com.chunbaetour.domain.search.dto.response.SearchFestivalResponse;
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

    /** 자동완성 최대 반환 건수 (SA 명세 F-SEARCH-003 §4. 최대 5개 반환) */
    private static final int SUGGEST_MAX_SIZE = 5;

    /** 자동완성 Redis 캐시 키 prefix. 실제 키: {@code search:suggest:{prefix}} */
    private static final String SUGGEST_CACHE_KEY_PREFIX = "search:suggest:";

    /** 자동완성 캐시 TTL — prefix는 관광지 데이터 변경이 잦지 않으므로 5분 캐싱으로 DB 부하 감소 */
    private static final Duration SUGGEST_CACHE_TTL = Duration.ofMinutes(5);

    /**
     * 인기 검색어 ZSet 키.
     * <p>
     * {@link PopularSearchService}의 {@code RANKING_KEY}와 동일한 값이어야 한다.
     * 두 클래스가 같은 Redis 키를 공유하므로, 키 이름 변경 시 반드시 양쪽을 함께 수정해야 한다.
     * PopularSearchService의 상수가 private이므로 여기서는 독립 선언한다.
     * </p>
     */
    private static final String POPULAR_RANKING_KEY = "search:ranking";

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
        keyword = normalized;

        // 1. 조회 (hasNext 판별을 위해 size + 1 개 조회)
        List<SearchPlaceResponse> items = placeQueryRepository.searchByKeyword(keyword, category, region, cursorId, size);

        // 2. hasNext 및 nextCursor 계산
        boolean hasNext = items.size() > size;
        List<SearchPlaceResponse> resultItems = hasNext ? items.subList(0, size) : items;

        Long nextCursor = resultItems.isEmpty() ? null : resultItems.get(resultItems.size() - 1).placeId();
        String nextCursorStr = nextCursor != null ? String.valueOf(nextCursor) : null;

        // 3. 인기 검색어 점수 집계 (유효한 키워드이고, 결과가 1건 이상 존재하며, 첫 페이지 요청일 때만)
        // 페이지네이션(cursorId != null) 시 검색 횟수가 중복으로 증가하는 어뷰징(Abuse)을 원천 차단한다.
        if (!resultItems.isEmpty() && cursorId == null) {
            popularSearchService.incrementSearchCount(keyword, clientIp);
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

        // 검색어 길이 제한 (최대 50자) (PLACE_006)
        if (keyword != null) {
            String normalized = keyword.strip();
            if (StringUtils.hasText(normalized) && normalized.length() > 50) {
                throw new BusinessException(ErrorCode.SEARCH_KEYWORD_TOO_LONG);
            }
            keyword = StringUtils.hasText(normalized) ? normalized : keyword;
        }

        // 1. 조회 (hasNext 판별을 위해 size + 1 개 조회)
        List<Festival> items = festivalQueryRepository.searchFestivals(keyword, startDate, endDate, region, cursorId, size);

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

        // 4. 인기 검색어 점수 집계 (유효한 키워드이고, 결과가 1건 이상 존재하며, 첫 페이지 요청일 때만)
        if (StringUtils.hasText(keyword) && !updatedItems.isEmpty() && cursorId == null) {
            popularSearchService.incrementSearchCount(keyword, clientIp);
        }

        return new CursorPageResponse<>(updatedItems, nextCursorStr, hasNext, updatedItems.size());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 검색어 자동완성 (Phase 2-4)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * 검색어 자동완성 (Phase 2-4).
     * <p>
     * SA 명세서: {@code GET /api/v1/search/suggest?q={prefix}}<br>
     * F-SEARCH-003: prefix 최소 1자, 최대 5개 반환.
     * </p>
     *
     * <p><b>통합 전략 (DB + Redis ZSet):</b>
     * <ol>
     *   <li>Redis 캐시({@code search:suggest:{prefix}}) 먼저 조회 → Hit 시 즉시 반환</li>
     *   <li>캐시 Miss 시:
     *     <ol>
     *       <li>DB에서 {@code LIKE 'prefix%'} 관광지명 최대 5개 조회</li>
     *       <li>Redis ZSet({@code search:ranking})에서 prefix로 시작하는 인기 검색어 조회하여 병합</li>
     *       <li>중복 제거 후 최대 {@link #SUGGEST_MAX_SIZE}개로 제한</li>
     *       <li>결과를 Redis List로 캐싱 (TTL {@link #SUGGEST_CACHE_TTL})</li>
     *     </ol>
     *   </li>
     * </ol>
     * </p>
     *
     * <p><b>[성능 고려]</b><br>
     * {@code LIKE 'prefix%'} 패턴은 {@code idx_places_name} B-Tree 인덱스의 Range Scan을
     * 활용할 수 있어 {@code LIKE '%keyword%'} 방식보다 훨씬 빠르다.
     * Redis 캐싱(5분 TTL)을 통해 동일 prefix 반복 입력 시 DB 조회를 생략한다.
     * </p>
     *
     * @param prefix   사용자가 입력 중인 검색어 prefix (non-null, 1자 이상)
     * @return 자동완성 후보 문자열 목록 (최대 5개, 중복 없음)
     * @throws BusinessException prefix가 null/blank인 경우 {@code SEARCH_KEYWORD_TOO_SHORT}
     * @throws BusinessException prefix가 50자를 초과하는 경우 {@code SEARCH_KEYWORD_TOO_LONG}
     */
    public List<String> suggest(String prefix) {
        log.info("[SearchService] 자동완성 요청 - prefixLength: {}", prefix != null ? prefix.length() : 0);

        // 1. 입력값 정규화 (양끝 공백 제거)
        String normalized = prefix != null ? prefix.strip() : null;

        // 2. 입력값 검증
        // [정책] prefix는 최소 1자 이상이어야 함 (SA 명세 F-SEARCH-003 §동작 방식 1. prefix 수신(최소 1자))
        if (!StringUtils.hasText(normalized)) {
            throw new BusinessException(ErrorCode.SEARCH_KEYWORD_TOO_SHORT);
        }
        // [정책] prefix가 50자를 초과하면 쿼리 비용이 커지므로 차단 (searchPlaces와 동일 제한)
        if (normalized.length() > 50) {
            throw new BusinessException(ErrorCode.SEARCH_KEYWORD_TOO_LONG);
        }

        // 3. Redis 캐시 먼저 조회 (캐시 Hit 시 즉시 반환)
        String cacheKey = SUGGEST_CACHE_KEY_PREFIX + normalized.toLowerCase();
        List<String> cached = stringRedisTemplate.opsForList().range(cacheKey, 0, -1);
        if (cached != null && !cached.isEmpty()) {
            log.debug("[SearchService] 자동완성 캐시 Hit - prefix: {}, count: {}", normalized, cached.size());
            return cached;
        }

        // 4. 캐시 Miss — DB + Redis ZSet 통합 조회
        // 4-1. DB: 관광지명 prefix LIKE 검색 (최대 SUGGEST_MAX_SIZE개)
        List<String> dbResults = placeQueryRepository.suggestByPrefix(normalized, SUGGEST_MAX_SIZE);

        // 4-2. Redis ZSet(search:ranking)에서 prefix로 시작하는 인기 검색어 조회
        //      ZSet 전체를 score 내림차순(인기순)으로 읽어 prefix 필터링
        //      [알려진 한계] 데이터 규모가 커지면 ZSet 순회 비용 증가 →
        //      추후 Trie 구조 또는 별도 sorted-set 설계로 개선 필요
        List<String> redisResults = fetchPopularSuggestions(normalized);

        // 4-3. DB 결과 우선, Redis 인기 검색어 보완 (중복 제거, 최대 5개)
        //      LinkedHashSet으로 삽입 순서(DB → Redis) 보존 + 중복 제거
        Set<String> merged = new LinkedHashSet<>(dbResults);
        for (String keyword : redisResults) {
            if (merged.size() >= SUGGEST_MAX_SIZE) {
                break;
            }
            merged.add(keyword);
        }
        List<String> result = new ArrayList<>(merged);

        // 5. 결과를 Redis List로 캐싱 (TTL SUGGEST_CACHE_TTL)
        if (!result.isEmpty()) {
            try {
                // 기존 캐시 키 삭제 후 재저장 (RPUSH 전 삭제하지 않으면 이전 값이 남음)
                stringRedisTemplate.delete(cacheKey);
                stringRedisTemplate.opsForList().rightPushAll(cacheKey, result);
                stringRedisTemplate.expire(cacheKey, SUGGEST_CACHE_TTL);
                log.debug("[SearchService] 자동완성 캐시 저장 - prefix: {}, count: {}", normalized, result.size());
            } catch (Exception e) {
                // 캐시 저장 실패는 응답에 영향을 주지 않도록 warn만 기록 (장애 격리)
                log.warn("[SearchService] 자동완성 캐시 저장 실패 (무시) - prefix: {}", normalized, e);
            }
        }

        return result;
    }

    /**
     * Redis ZSet({@code search:ranking})에서 prefix로 시작하는 인기 검색어를 조회한다.
     * <p>
     * score 내림차순(인기순)으로 최대 전체 랭킹을 순회하여 prefix 매칭하는 항목을 추출한다.
     * SA 명세 §3. 검색(Search): "Redis ZSet prefix 매칭 병행 (선택)"에 해당하는 선택적 보완 로직.
     * </p>
     *
     * @param prefix 매칭할 prefix (소문자 정규화 전)
     * @return prefix로 시작하는 인기 검색어 목록 (최대 SUGGEST_MAX_SIZE 건)
     */
    private List<String> fetchPopularSuggestions(String prefix) {
        try {
            // 인기 검색어 TOP 100 기준으로 순회 (자동완성 보완이 목적이므로 전체를 읽지 않음)
            Set<ZSetOperations.TypedTuple<String>> rankingSet =
                    stringRedisTemplate.opsForZSet().reverseRangeWithScores(
                            POPULAR_RANKING_KEY, 0, 99);

            if (rankingSet == null || rankingSet.isEmpty()) {
                return List.of();
            }

            List<String> matched = new ArrayList<>();
            String lowerPrefix = prefix.toLowerCase();

            for (ZSetOperations.TypedTuple<String> tuple : rankingSet) {
                String keyword = tuple.getValue();
                if (keyword == null || keyword.isBlank()) {
                    continue;
                }
                // case-insensitive prefix 매칭 — 사용자 입력 대소문자와 무관하게 매칭
                if (keyword.toLowerCase().startsWith(lowerPrefix)) {
                    matched.add(keyword);
                }
                if (matched.size() >= SUGGEST_MAX_SIZE) {
                    break;
                }
            }
            return matched;
        } catch (Exception e) {
            // Redis 장애 시 DB 결과만으로 응답 (자동완성 보완은 선택적 기능)
            log.warn("[SearchService] Redis ZSet 자동완성 조회 실패 (무시) - prefix: {}", prefix, e);
            return List.of();
        }
    }
}
