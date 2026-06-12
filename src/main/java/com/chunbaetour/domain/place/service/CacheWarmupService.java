package com.chunbaetour.domain.place.service;

import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.constant.PlaceRedisConstants;
import com.chunbaetour.domain.place.dto.response.PlaceCacheDto;
import com.chunbaetour.domain.place.repository.PlaceRepository;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 캐시 웜업(Cache Warm-up) 서비스 (KAN-278).
 *
 * <p>서버가 완전히 기동된 뒤({@code ApplicationReadyEvent}) 비동기로 실행되어
 * 콜드 스타트 시 첫 요청에서 발생하는 DB 부하를 선제적으로 차단한다.
 *
 * <h3>웜업 대상</h3>
 * <ol>
 *   <li><b>인기 관광지 ZSet</b> ({@code recommend:popular}) — Top {@value PlaceRedisConstants#CACHE_WARMUP_ZSET_TOP_N} 인기 점수 적재</li>
 *   <li><b>관광지 상세 캐시</b> ({@code place:{id}}) — 인기 Top {@value PlaceRedisConstants#CACHE_WARMUP_DETAIL_TOP_N}개 선적재</li>
 * </ol>
 *
 * <h3>설계 원칙</h3>
 * <ul>
 *   <li>각 단계는 독립적으로 실행되며, 한 단계가 실패해도 나머지는 계속 진행한다.</li>
 *   <li>{@code @Async}로 메인 스레드를 블록하지 않으므로 서버 응답 시간에 영향이 없다.</li>
 *   <li>상세 캐시 적재 시 {@value PlaceRedisConstants#CACHE_WARMUP_INTERVAL_MS}ms 간격으로 DB/Redis 부하를 분산한다.</li>
 *   <li>이미 캐시가 있으면 덮어쓰지 않아 재시작 시 불필요한 DB 조회를 방지한다.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheWarmupService {

    private final PlaceRepository placeRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final PlaceDetailEnrichmentService placeDetailEnrichmentService;

    /**
     * 서버 완전 기동 후 비동기 웜업을 시작한다.
     * {@code ApplicationReadyEvent}는 컨텍스트 초기화·DB 연결·트랜잭션 인프라가 모두 준비된 시점에 발행된다.
     */
    @Async("cacheWarmupExecutor")
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("[CacheWarmup] 캐시 웜업 시작");

        // 1. 다중 인스턴스 분산 락 (5분)
        String lockKey = PlaceRedisConstants.CACHE_WARMUP_LOCK_KEY;
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", Duration.ofMinutes(5));
        if (!Boolean.TRUE.equals(acquired)) {
            log.info("[CacheWarmup] 다른 인스턴스에서 웜업 중이거나 이미 완료됨 — 스킵");
            return;
        }

        try {
            // 2. DB에서 넉넉한 후보군 조회 (Top 100)
            List<Place> dbCandidates = placeRepository.findTopPopularPlaces(
                    PlaceRedisConstants.POPULAR_LIKE_WEIGHT,
                    PlaceRedisConstants.POPULAR_VIEW_WEIGHT,
                    PageRequest.of(0, PlaceRedisConstants.CACHE_WARMUP_DB_CANDIDATE_TOP_N)
            );

            if (dbCandidates.isEmpty()) {
                log.warn("[CacheWarmup] DB 조회 결과 없음 — 웜업 스킵");
                return;
            }

            // 3. Redis 최신 카운터를 반영하여 재계산 및 정렬 후 Top 20 컷
            List<Place> rescoredTopPlaces = dbCandidates.stream()
                    .map(p -> new PlaceScore(p, calculateRedisBasedScore(p)))
                    .sorted((p1, p2) -> Double.compare(p2.score(), p1.score()))
                    .limit(PlaceRedisConstants.CACHE_WARMUP_DETAIL_TOP_N)
                    .map(ps -> ps.place())
                    .toList();

            // 4. 그 중 Top 10을 ZSet 웜업에 전달
            List<Place> popularPlaces = rescoredTopPlaces.stream()
                    .limit(PlaceRedisConstants.CACHE_WARMUP_ZSET_TOP_N)
                    .toList();

            warmupPopularZSet(popularPlaces);
            warmupPlaceDetails(rescoredTopPlaces);

            log.info("[CacheWarmup] 캐시 웜업 완료");
        } catch (Exception e) {
            log.error("[CacheWarmup] 캐시 웜업 전체 실패", e);
        } finally {
            // 웜업 완료 또는 실패 시 락 명시적 해제
            try {
                stringRedisTemplate.delete(lockKey);
            } catch (Exception ignore) {}
        }
    }

    // ── 1단계: 인기 추천 ZSet 웜업 ─────────────────────────────────────────────

    /**
     * {@code recommend:popular} ZSet에 인기 Top {@value PlaceRedisConstants#CACHE_WARMUP_ZSET_TOP_N} 관광지 점수를 미리 적재한다.
     *
     * <p>이미 키가 존재하면(재시작 직후 TTL이 살아있는 경우) 불필요한 DB 조회를 생략한다.
     */
    public void warmupPopularZSet(List<Place> popularPlaces) {
        if (popularPlaces.isEmpty()) {
            log.warn("[CacheWarmup] 인기 추천 웜업 — 적재 대상 없음");
            return;
        }

        String key = PlaceRedisConstants.RECOMMEND_POPULAR_KEY;
        try {
            // 이미 캐시에 온전한 데이터(Size >= N, TTL > 0)가 있으면 건너뜀
            Long existingSize = stringRedisTemplate.opsForZSet().zCard(key);
            Long expire = stringRedisTemplate.getExpire(key);
            if (existingSize != null && existingSize >= PlaceRedisConstants.CACHE_WARMUP_ZSET_TOP_N
                    && expire != null && expire > 0) {
                log.info("[CacheWarmup] 인기 추천 ZSet 정상 캐시 존재 — 웜업 생략 (size={})", existingSize);
                return;
            }

            log.info("[CacheWarmup] 인기 추천 ZSet 웜업 시작");

            Set<TypedTuple<String>> tuples = new HashSet<>();
            for (Place place : popularPlaces) {
                double score = calculateRedisBasedScore(place);
                tuples.add(new DefaultTypedTuple<>(String.valueOf(place.getId()), score));
            }

            try {
                stringRedisTemplate.opsForZSet().add(key, tuples);
                Boolean expired = stringRedisTemplate.expire(key, PlaceRedisConstants.RECOMMEND_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
                if (Boolean.FALSE.equals(expired)) {
                    throw new IllegalStateException("expire 연산이 false를 반환했습니다");
                }
            } catch (Exception e) {
                // Partial Write 방지: ZSet 적재 후 TTL 설정에 실패한 경우 쓰레기 데이터 영구 존속을 막기 위해 키 삭제
                try {
                    stringRedisTemplate.delete(key);
                } catch (Exception ignore) {}
                throw new IllegalStateException("ZSet 적재 및 TTL 설정 중 예외 발생 — rollback 완료", e);
            }

            log.info("[CacheWarmup] 인기 추천 ZSet 웜업 완료 — {}건 적재", popularPlaces.size());
        } catch (Exception e) {
            log.warn("[CacheWarmup] 인기 추천 ZSet 웜업 실패 — 이후 요청은 DB Fallback으로 처리됨", e);
        }
    }

    // ── 2단계: 관광지 상세 캐시 웜업 ───────────────────────────────────────────

    /**
     * 인기 Top {@value PlaceRedisConstants#CACHE_WARMUP_DETAIL_TOP_N}개 관광지의 상세 캐시를 미리 적재한다.
     *
     * <p>이미 캐시가 존재하는 관광지는 건너뛰어 불필요한 덮어쓰기를 방지한다.
     * 각 관광지 간 {@value PlaceRedisConstants#CACHE_WARMUP_INTERVAL_MS}ms 간격으로 DB·Redis 부하를 분산한다.
     */
    public void warmupPlaceDetails(List<Place> topPlaces) {
        try {
            log.info("[CacheWarmup] 관광지 상세 캐시 웜업 시작 (Top {})", PlaceRedisConstants.CACHE_WARMUP_DETAIL_TOP_N);

            if (topPlaces.isEmpty()) {
                log.warn("[CacheWarmup] 관광지 상세 웜업 — 적재 대상 없음");
                return;
            }

            int warmedUp = 0;
            int skipped = 0;

            for (Place place : topPlaces) {
                try {
                    String cacheKey = PlaceRedisConstants.PLACE_DETAIL_CACHE_PREFIX + place.getId();

                    // 이미 캐시가 있으면 건너뜀
                    Boolean exists = stringRedisTemplate.hasKey(cacheKey);
                    if (Boolean.TRUE.equals(exists)) {
                        skipped++;
                        continue;
                    }

                    place = placeDetailEnrichmentService.enrichIfNeeded(place);

                    // enrichment 미완료(needsDetailEnrichment=true)면 캐시 오염 방지를 위해 skip
                    if (place.needsDetailEnrichment()) {
                        skipped++;
                        continue;
                    }

                    int likeCount = resolveRedisCount(PlaceRedisConstants.PLACE_LIKE_COUNT_PREFIX, place.getId(), place.getLikeCount());

                    // imageUrls JSON 파싱
                    List<String> imageUrls = parseImageUrls(place.getImageUrls());
                    PlaceCacheDto cacheDto = PlaceCacheDto.of(place, imageUrls, likeCount);

                    String json = objectMapper.writeValueAsString(cacheDto);
                    
                    // 중간에 사용자 요청으로 캐시가 생성되었을 수 있으므로 setIfAbsent 사용
                    stringRedisTemplate.opsForValue().setIfAbsent(
                            cacheKey, json,
                            Duration.ofMinutes(PlaceRedisConstants.PLACE_DETAIL_CACHE_TTL_MINUTES)
                    );
                    warmedUp++;
                } catch (Exception e) {
                    log.warn("[CacheWarmup] 관광지 상세 개별 캐시 실패 — placeId={}", place.getId(), e);
                } finally {
                    // 부하 분산 인터벌 (실패/건너뜀 상관없이 무조건 간격 유지)
                    try {
                        Thread.sleep(PlaceRedisConstants.CACHE_WARMUP_INTERVAL_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("[CacheWarmup] 관광지 상세 웜업 인터럽트 — 중단");
                        break;
                    }
                }
            }

            log.info("[CacheWarmup] 관광지 상세 캐시 웜업 완료 — 적재: {}건, 스킵: {}건", warmedUp, skipped);
        } catch (Exception e) {
            log.warn("[CacheWarmup] 관광지 상세 캐시 웜업 실패 — 이후 요청은 DB Fallback으로 처리됨", e);
        }
    }

    // ── 헬퍼 ────────────────────────────────────────────────────────────────────

    private int resolveRedisCount(String prefix, Long placeId, int dbFallback) {
        try {
            String val = stringRedisTemplate.opsForValue().get(prefix + placeId);
            return (val != null) ? Integer.parseInt(val) : dbFallback;
        } catch (Exception e) {
            log.warn("[CacheWarmup] Redis count 조회 실패, DB 값 사용: key={}{}, err={}", prefix, placeId, e.getMessage());
            return dbFallback;
        }
    }

    private List<String> parseImageUrls(String imageUrlsJson) {
        if (imageUrlsJson == null || imageUrlsJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(imageUrlsJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("[CacheWarmup] imageUrls JSON 파싱 실패: {}", imageUrlsJson, e);
            return Collections.emptyList();
        }
    }

    private double calculateRedisBasedScore(Place place) {
        int likeCount = resolveRedisCount(PlaceRedisConstants.PLACE_LIKE_COUNT_PREFIX, place.getId(), place.getLikeCount());
        int viewCount = resolveRedisCount(PlaceRedisConstants.PLACE_VIEW_COUNT_PREFIX, place.getId(), place.getViewCount());
        return (likeCount * PlaceRedisConstants.POPULAR_LIKE_WEIGHT)
                + (viewCount * PlaceRedisConstants.POPULAR_VIEW_WEIGHT);
    }

    private record PlaceScore(Place place, double score) {}
}
