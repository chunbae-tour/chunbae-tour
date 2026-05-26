package com.chunbaetour.domain.place.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.dto.response.NearbyPlacePageResponse;
import com.chunbaetour.domain.place.dto.response.NearbyPlaceResponse;
import com.chunbaetour.domain.place.dto.response.PlaceCacheDto;
import com.chunbaetour.domain.place.dto.response.PlaceDetailResponse;
import com.chunbaetour.domain.place.repository.PlaceQueryRepository;
import com.chunbaetour.domain.place.repository.PlaceRepository;
import com.chunbaetour.domain.place.type.PlaceStatus;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceService {

    private final PlaceRepository placeRepository;
    private final PlaceQueryRepository placeQueryRepository;
    private final PlaceLikeService placeLikeService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final String PLACE_DETAIL_CACHE_PREFIX = "place:";
    private static final String PLACE_VIEW_COUNT_PREFIX   = "place:view:";
    private static final String PLACE_LIKE_COUNT_PREFIX   = "place:like:";
    private static final Duration PLACE_DETAIL_TTL         = Duration.ofMinutes(10);

    @Transactional(readOnly = true)
    public NearbyPlacePageResponse findNearby(double lat, double lng, double radius, Long cursor, Double cursorDistance, int size) {
        // 캐시 키 생성: 좌표 소수점 3자리 반올림 및 반경 정수형 변환
        String latRounded = String.format("%.3f", lat);
        String lngRounded = String.format("%.3f", lng);
        double radiusRounded = Math.round(radius);
        String cacheKey = String.format("nearby:%s:%s:%.0f:%d", latRounded, lngRounded, radiusRounded, size);

        // 첫 페이지일 경우에만 캐시 조회 (0L도 첫 페이지로 간주)
        boolean isFirstPage = (cursor == null || cursor == 0L);
        if (isFirstPage) {
            String cachedData = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cachedData != null) {
                try {
                    log.debug("Redis Cache Hit");
                    return objectMapper.readValue(cachedData, new TypeReference<>() {});
                } catch (Exception e) {
                    log.error("Redis Cache parsing error", e);
                }
            }
        }

        // DB 쿼리 (Haversine) - 다음 페이지 존재 여부 확인을 위해 size + 1 요청
        log.info("Redis Cache Miss or Paging: Fetching from DB");
        List<NearbyPlaceResponse> places = placeQueryRepository.findNearbyPlaces(lat, lng, radius, cursor, cursorDistance, size + 1);

        boolean hasNext = false;
        if (places.size() > size) {
            hasNext = true;
            places.remove(size); // 초과 조회한 마지막 요소 제거
        }
        
        NearbyPlacePageResponse pageResponse = new NearbyPlacePageResponse(places, hasNext);

        // 첫 페이지 결과 캐싱 (TTL 5분)
        if (isFirstPage && !places.isEmpty()) {
            try {
                String json = objectMapper.writeValueAsString(pageResponse);
                stringRedisTemplate.opsForValue().set(cacheKey, json, Duration.ofMinutes(5));
            } catch (Exception e) {
                log.error("Redis Cache writing error", e);
            }
        }

        return pageResponse;
    }

    /**
     * 관광지 상세 조회
     * 1. Redis 캐시 조회 (key: place:{placeId}, TTL 10분)
     * 2. Miss → DB 조회 → 캐싱
     * 3. 조회수 원자적 증가: INCR place:view:{placeId}
     * 4. PHASE 3: 로그인 사용자의 찜 여부(isLiked) 반영
     *
     * @param placeId 조회할 관광지 ID
     * @param userId  로그인 사용자 ID (비로그인 시 null)
     * @return PlaceDetailResponse
     */
    public PlaceDetailResponse findById(Long placeId, Long userId) {
        PlaceDetailResponse response = resolveFromCacheOrDb(placeId, userId);
        // 조회수 증가 (단일 책임 원칙, 항상 마지막에 1회 호출)
        incrementViewCount(placeId);
        return response;
    }

    private static final String UNLOCK_LUA_SCRIPT = 
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "return redis.call('del', KEYS[1]) else return 0 end";

    private PlaceDetailResponse resolveFromCacheOrDb(Long placeId, Long userId) {
        String cacheKey = PLACE_DETAIL_CACHE_PREFIX + placeId;

        // 1. Redis 캐시 1차 조회
        PlaceCacheDto cached = getFromCache(cacheKey, placeId);
        if (cached != null) {
            boolean isLiked = resolveIsLiked(userId, placeId);
            return PlaceDetailResponse.of(cached, isLiked);
        }

        // Cache Stampede 방어용 Lock
        String lockKey = "lock:place:" + placeId;
        String lockValue = java.util.UUID.randomUUID().toString();
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, Duration.ofSeconds(3));

        if (Boolean.TRUE.equals(locked)) {
            try {
                PlaceCacheDto cacheDto = fetchAndCache(placeId, cacheKey);
                boolean isLiked = resolveIsLiked(userId, placeId);
                return PlaceDetailResponse.of(cacheDto, isLiked);
            } finally {
                releaseLockIfOwner(lockKey, lockValue);
            }
        } else {
            // 락 획득 실패 시 Bounded Polling (최대 20회 * 50ms = 1초 대기)
            for (int i = 0; i < 20; i++) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }

                PlaceCacheDto retryCached = getFromCache(cacheKey, placeId);
                if (retryCached != null) {
                    boolean isLiked = resolveIsLiked(userId, placeId);
                    return PlaceDetailResponse.of(retryCached, isLiked);
                }
            }
            // 폴링 타임아웃 이후에도 캐시가 없으면 최후의 수단으로 DB 쿼리 진행
            PlaceCacheDto cacheDto = fetchAndCache(placeId, cacheKey);
            boolean isLiked = resolveIsLiked(userId, placeId);
            return PlaceDetailResponse.of(cacheDto, isLiked);
        }
    }

    /**
     * Redis 캐시에서 PlaceCacheDto 조회.
     * isLiked 같은 사용자 개인화 데이터는 캐시에 포함되지 않으므로 호출자가 별도로 처리한다.
     */
    private PlaceCacheDto getFromCache(String cacheKey, Long placeId) {
        String cachedData = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cachedData != null) {
            try {
                log.debug("Place Detail Cache Hit: placeId={}", placeId);
                return objectMapper.readValue(cachedData, PlaceCacheDto.class);
            } catch (Exception e) {
                log.error("Place Detail Cache parsing error: placeId={}", placeId, e);
                stringRedisTemplate.delete(cacheKey); // 깨진 캐시 즉시 제거
            }
        }
        return null;
    }

    /**
     * 찜 여부 조회 — PlaceLikeService에 위임해 찜 관련 책임을 단일화.
     * 조회 실패 시 예외를 터뜨리지 않고 false를 반환해 상세 조회 흐름을 보호.
     */
    private boolean resolveIsLiked(Long userId, Long placeId) {
        try {
            return placeLikeService.isLiked(userId, placeId);
        } catch (Exception e) {
            log.warn("isLiked 조회 실패, false로 처리: userId={}, placeId={}", userId, placeId, e);
            return false;
        }
    }

    private void releaseLockIfOwner(String lockKey, String lockValue) {
        try {
            stringRedisTemplate.execute(
                    new org.springframework.data.redis.core.script.DefaultRedisScript<>(UNLOCK_LUA_SCRIPT, Long.class),
                    Collections.singletonList(lockKey),
                    lockValue
            );
        } catch (Exception e) {
            log.error("Failed to release lock: lockKey={}", lockKey, e);
        }
    }

    /**
     * DB에서 관광지를 조회하고 캐시에 저장한 뒤 PlaceCacheDto를 반환.
     *
     * <p>likeCount는 SA 설계(건 Place.java 주석: "Redis INCR/DECR 후 배치로 DB 동기화")에 따라
     * Redis {@code place:like:{placeId}} 카운터 값으로 오버라이드한다.
     * Redis 조회 실패 시 DB 스냅샷 값을 그대로 사용(방어 처리).
     * isLiked는 캐시에 저장하지 않으며, 호출자가 별도로 계산한다.
     */
    private PlaceCacheDto fetchAndCache(Long placeId, String cacheKey) {
        log.debug("Place Detail Cache Miss: Fetching from DB, placeId={}", placeId);
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND));

        if (place.getStatus() != PlaceStatus.ACTIVE) {
            log.debug("Place is not ACTIVE: placeId={}, status={}", placeId, place.getStatus());
            throw new BusinessException(ErrorCode.PLACE_NOT_FOUND);
        }

        List<String> imageUrlList = parseImageUrls(place.getImageUrls());

        // Redis 카운터에서 likeCount 케시 오버라이드
        // Redis 에 없으면 DB 값 그대로, 있으면 Redis 실시간 값 우선
        int likeCount = resolveRedisLikeCount(placeId, place.getLikeCount());
        PlaceCacheDto cacheDto = PlaceCacheDto.of(place, imageUrlList, likeCount);

        // 캐싱 (TTL 10분)
        try {
            String json = objectMapper.writeValueAsString(cacheDto);
            stringRedisTemplate.opsForValue().set(cacheKey, json, PLACE_DETAIL_TTL);
        } catch (Exception e) {
            log.error("Place Detail Cache writing error: placeId={}", placeId, e);
        }

        return cacheDto;
    }

    /**
     * Redis {@code place:like:{placeId}} 카운터를 읽어 likeCount를 반환.
     * 코드가 없거나 파싱 실패 시 DB 값으로 fallback.
     */
    private int resolveRedisLikeCount(Long placeId, int dbFallback) {
        try {
            String val = stringRedisTemplate.opsForValue().get(PLACE_LIKE_COUNT_PREFIX + placeId);
            return (val != null) ? Integer.parseInt(val) : dbFallback;
        } catch (Exception e) {
            log.warn("Redis likeCount 조회 실패, DB 값 사용: placeId={}", placeId, e);
            return dbFallback;
        }
    }

    private void incrementViewCount(Long placeId) {
        try {
            stringRedisTemplate.opsForValue().increment(PLACE_VIEW_COUNT_PREFIX + placeId);
        } catch (Exception e) {
            log.warn("Place view count increment failed: placeId={}", placeId, e);
        }
    }

    /**
     * imageUrls JSON 문자열을 List<String>으로 파싱
     * 파싱 실패 시 빈 리스트 반환 (null-safe)
     */
    private List<String> parseImageUrls(String imageUrlsJson) {
        if (imageUrlsJson == null || imageUrlsJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(imageUrlsJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("imageUrls JSON parsing failed: {}", imageUrlsJson, e);
            return Collections.emptyList();
        }
    }
}

