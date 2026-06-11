package com.chunbaetour.domain.search.service;

import com.chunbaetour.domain.like.dto.CategoryCount;
import com.chunbaetour.domain.like.repository.UserLikeRepository;
import com.chunbaetour.domain.place.event.PlaceLikeChangedEvent;
import com.chunbaetour.domain.place.type.PlaceCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 검색 결과 개인화 서비스 (Phase 9-2).
 * <p>
 * 로그인한 유저가 찜(Like)한 관광지의 카테고리 패턴을 분석하여 선호 카테고리를 추출하고,
 * 검색 결과 재정렬 시 사용할 부스팅 카테고리 목록을 반환한다.
 * </p>
 *
 * <b>[캐싱 전략]</b><br>
 * 선호 카테고리는 Redis에 {@code TTL = 10분}으로 캐싱한다.
 * 찜 목록은 자주 변하지 않고, 검색할 때마다 DB를 조회하면 불필요한 부하가 발생하므로
 * 단기 캐싱으로 DB 조회를 최소화한다.
 * </p>
 *
 * <b>[Redis Key 구조]</b><br>
 * {@code search:personalization:user:{userId}:categories} — PlaceCategory 이름들을 쉼표(,)로 구분한 문자열
 * </p>
 *
 * <b>[예외 처리]</b><br>
 * Redis 장애, DB 조회 실패 등 모든 예외는 catch하여 빈 리스트를 반환한다.
 * 개인화 실패가 검색 전체를 막아서는 안 되므로, 실패 시 일반 검색으로 graceful degradation한다.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchPlacePersonalizationService {

    private final UserLikeRepository userLikeRepository;
    private final StringRedisTemplate stringRedisTemplate;

    /** 선호 카테고리 캐시 TTL: 찜 목록이 자주 바뀌지 않으므로 10분으로 설정 */
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    /** Redis 캐시 키 접두사 */
    private static final String CACHE_KEY_PREFIX = "search:personalization:user:";

    /** 캐시 키 접미사 */
    private static final String CACHE_KEY_SUFFIX = ":categories";

    /**
     * 유저의 선호 카테고리 목록을 반환한다 (최대 3개).
     * <p>
     * Redis 캐시를 먼저 확인하고, 없으면 DB에서 찜한 관광지의 카테고리 빈도를 집계하여 반환한다.
     * 모든 예외 상황에서는 빈 리스트를 반환하여 일반 검색으로 graceful degradation한다.
     * </p>
     *
     * @param userId 유저 PK (null이면 빈 리스트 반환)
     * @return 선호 카테고리 목록 (count 내림차순, 최대 3개). 찜 이력 없으면 빈 리스트.
     */
    public List<PlaceCategory> getPreferredCategories(Long userId) {
        // 비로그인 유저는 개인화 불필요 → 즉시 빈 리스트 반환
        if (userId == null) {
            return Collections.emptyList();
        }

        try {
            // 1. Redis 캐시 조회 시도
            String cacheKey = buildCacheKey(userId);
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cached != null && !cached.isBlank()) {
                return parseCachedCategories(cached);
            }

            // 2. DB에서 카테고리별 찜 count 집계 — DB LIMIT 3으로 최대 3개만 조회
            List<CategoryCount> rows = userLikeRepository.findLikedPlaceCategoryCountsByUserId(userId);

            // 찜한 관광지가 없으면 캐싱 없이 빈 리스트 반환 (캐싱하면 찜 직후에도 10분간 빈 값이 반환됨)
            if (rows.isEmpty()) {
                return Collections.emptyList();
            }

            // DB 쿼리에서 LIMIT 3을 이미 적용했으므로 추가 slice 불필요
            List<PlaceCategory> categories = rows.stream()
                    .map(CategoryCount::getCategory)
                    .collect(Collectors.toList());

            // 3. Redis에 캐싱 (TTL 10분)
            String serialized = categories.stream()
                    .map(PlaceCategory::name)
                    .collect(Collectors.joining(","));
            stringRedisTemplate.opsForValue().set(cacheKey, serialized, CACHE_TTL);

            log.debug("[Personalization] userId={} preferredCategories={}", userId, categories);
            return categories;

        } catch (Exception e) {
            // Redis/DB 장애 시 개인화 실패 → 일반 검색으로 graceful degradation
            log.warn("[Personalization] 선호 카테고리 조회 실패. userId={}, 일반 검색으로 대체. error={}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 유저의 선호 카테고리 캐시를 즉시 무효화한다.
     * <p>
     * 유저가 찜/찜 취소 액션을 할 때 호출하여 다음 검색부터 최신 선호가 반영되도록 한다.
     * (현재는 TTL 만료 방식으로 동작하며, 향후 이벤트 기반 무효화로 업그레이드 가능)
     * </p>
     *
     * @param userId 유저 PK
     */
    public void evictCache(Long userId) {
        if (userId == null) return;
        try {
            stringRedisTemplate.delete(buildCacheKey(userId));
            log.debug("[Personalization] 캐시 무효화 완료. userId={}", userId);
        } catch (Exception e) {
            log.warn("[Personalization] 캐시 무효화 실패. userId={}, error={}", userId, e.getMessage());
        }
    }

    /**
     * 찜 변경 이벤트를 수신하여 선호 카테고리 캐시를 즉시 무효화한다.
     * 트랜잭션이 성공적으로 커밋된 후에만 실행된다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPlaceLikeChanged(PlaceLikeChangedEvent event) {
        evictCache(event.userId());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 내부 헬퍼
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Redis 캐시 키를 생성한다.
     * 예: search:personalization:user:42:categories
     */
    private String buildCacheKey(Long userId) {
        return CACHE_KEY_PREFIX + userId + CACHE_KEY_SUFFIX;
    }

    /**
     * Redis에 저장된 쉼표 구분 카테고리 문자열을 List<PlaceCategory>로 역직렬화한다.
     * 알 수 없는 카테고리 이름은 조용히 건너뛴다 (forward compatibility).
     */
    private List<PlaceCategory> parseCachedCategories(String cached) {
        try {
            return List.of(cached.split(",")).stream()
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .map(name -> {
                        try {
                            return PlaceCategory.valueOf(name);
                        } catch (IllegalArgumentException e) {
                            log.warn("[Personalization] 캐시에서 알 수 없는 카테고리 값 감지 (무시): {}", name);
                            return null;
                        }
                    })
                    .filter(c -> c != null)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[Personalization] 캐시 역직렬화 실패. cached={}, error={}", cached, e.getMessage());
            return Collections.emptyList();
        }
    }
}
