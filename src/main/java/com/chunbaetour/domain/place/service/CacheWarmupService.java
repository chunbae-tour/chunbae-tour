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
import org.springframework.transaction.annotation.Transactional;
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
 *   <li><b>인기 관광지 ZSet</b> ({@code recommend:popular}) — Top 10 인기 점수 적재</li>
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

        try {
            List<Place> topPlaces = placeRepository.findTopPopularPlaces(
                    PlaceRedisConstants.POPULAR_LIKE_WEIGHT,
                    PlaceRedisConstants.POPULAR_VIEW_WEIGHT,
                    PageRequest.of(0, PlaceRedisConstants.CACHE_WARMUP_DETAIL_TOP_N)
            );

            if (topPlaces.isEmpty()) {
                log.warn("[CacheWarmup] DB 조회 결과 없음 — 웜업 스킵");
                return;
            }

            // Top 10개를 ZSet 웜업에 전달
            List<Place> popularPlaces = topPlaces.subList(0, Math.min(topPlaces.size(), 10));
            warmupPopularZSet(popularPlaces);
            warmupPlaceDetails(topPlaces);

            log.info("[CacheWarmup] 캐시 웜업 완료");
        } catch (Exception e) {
            log.error("[CacheWarmup] 캐시 웜업 전체 실패", e);
        }
    }

    // ── 1단계: 인기 추천 ZSet 웜업 ─────────────────────────────────────────────

    /**
     * {@code recommend:popular} ZSet에 인기 Top 10 관광지 점수를 미리 적재한다.
     *
     * <p>이미 키가 존재하면(재시작 직후 TTL이 살아있는 경우) 불필요한 DB 조회를 생략한다.
     */
    public void warmupPopularZSet(List<Place> popularPlaces) {
        String key = PlaceRedisConstants.RECOMMEND_POPULAR_KEY;
        try {
            // 이미 캐시에 데이터가 있으면 건너뜀
            Long existingSize = stringRedisTemplate.opsForZSet().zCard(key);
            if (existingSize != null && existingSize > 0) {
                log.info("[CacheWarmup] 인기 추천 ZSet 이미 존재 — 웜업 생략 (size={})", existingSize);
                return;
            }

            log.info("[CacheWarmup] 인기 추천 ZSet 웜업 시작");

            if (popularPlaces.isEmpty()) {
                log.warn("[CacheWarmup] 인기 추천 웜업 — 적재 대상 없음");
                return;
            }

            Set<TypedTuple<String>> tuples = new HashSet<>();
            for (Place place : popularPlaces) {
                double score = (place.getLikeCount() * PlaceRedisConstants.POPULAR_LIKE_WEIGHT)
                        + (place.getViewCount() * PlaceRedisConstants.POPULAR_VIEW_WEIGHT);
                tuples.add(new DefaultTypedTuple<>(String.valueOf(place.getId()), score));
            }

            stringRedisTemplate.opsForZSet().add(key, tuples);
            stringRedisTemplate.expire(key, PlaceRedisConstants.RECOMMEND_CACHE_TTL_MINUTES, TimeUnit.MINUTES);

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
                String cacheKey = PlaceRedisConstants.PLACE_DETAIL_CACHE_PREFIX + place.getId();

                // 이미 캐시가 있으면 건너뜀
                Boolean exists = stringRedisTemplate.hasKey(cacheKey);
                if (Boolean.TRUE.equals(exists)) {
                    skipped++;
                    continue;
                }

                try {
                    place = placeDetailEnrichmentService.enrichIfNeeded(place);

                    // enrichment 미완료(needsDetailEnrichment=true)면 캐시 오염 방지를 위해 skip
                    if (place.needsDetailEnrichment()) {
                        skipped++;
                        continue;
                    }

                    int likeCount = resolveRedisLikeCount(place.getId(), place.getLikeCount());

                    // imageUrls JSON 파싱
                    List<String> imageUrls = parseImageUrls(place.getImageUrls());
                    PlaceCacheDto cacheDto = PlaceCacheDto.of(place, imageUrls, likeCount);

                    String json = objectMapper.writeValueAsString(cacheDto);
                    stringRedisTemplate.opsForValue().set(
                            cacheKey, json,
                            Duration.ofMinutes(PlaceRedisConstants.PLACE_DETAIL_CACHE_TTL_MINUTES)
                    );
                    warmedUp++;
                } catch (Exception e) {
                    log.warn("[CacheWarmup] 관광지 상세 개별 캐시 실패 — placeId={}", place.getId(), e);
                }

                // 부하 분산 인터벌
                try {
                    Thread.sleep(PlaceRedisConstants.CACHE_WARMUP_INTERVAL_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("[CacheWarmup] 관광지 상세 웜업 인터럽트 — 중단");
                    break;
                }
            }

            log.info("[CacheWarmup] 관광지 상세 캐시 웜업 완료 — 적재: {}건, 스킵: {}건", warmedUp, skipped);
        } catch (Exception e) {
            log.warn("[CacheWarmup] 관광지 상세 캐시 웜업 실패 — 이후 요청은 DB Fallback으로 처리됨", e);
        }
    }

    // ── 헬퍼 ────────────────────────────────────────────────────────────────────

    private int resolveRedisLikeCount(Long placeId, int dbFallback) {
        try {
            String val = stringRedisTemplate.opsForValue()
                    .get(PlaceRedisConstants.PLACE_LIKE_COUNT_PREFIX + placeId);
            return (val != null) ? Integer.parseInt(val) : dbFallback;
        } catch (Exception e) {
            log.warn("[CacheWarmup] Redis likeCount 조회 실패, DB 값 사용: placeId={}", placeId, e);
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
}
