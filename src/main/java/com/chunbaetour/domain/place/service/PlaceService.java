package com.chunbaetour.domain.place.service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.client.KakaoLocalApiClient;
import com.chunbaetour.domain.place.constant.PlaceRedisConstants;
import com.chunbaetour.domain.place.dto.Coord;
import com.chunbaetour.domain.place.dto.KakaoCategoryResponse;
import com.chunbaetour.domain.place.dto.request.PlaceListRequest;
import com.chunbaetour.domain.place.dto.response.NearbyCategoryPlacesResponse;
import com.chunbaetour.domain.place.dto.response.NearbyPlacePageResponse;
import com.chunbaetour.domain.place.dto.response.NearbyPlaceResponse;
import com.chunbaetour.domain.place.dto.response.PlaceCacheDto;
import com.chunbaetour.domain.place.dto.response.PlaceDetailResponse;
import com.chunbaetour.domain.place.dto.response.PlaceListResponse;
import com.chunbaetour.domain.place.repository.PlaceQueryRepository;
import com.chunbaetour.domain.place.repository.PlaceRepository;
import com.chunbaetour.domain.place.type.NearbyCategory;
import com.chunbaetour.domain.place.type.PlaceStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceService {

    private final PlaceRepository placeRepository;
    private final PlaceQueryRepository placeQueryRepository;
    private final PlaceLikeService placeLikeService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final KakaoLocalApiClient kakaoLocalApiClient;

    private static final Duration PLACE_DETAIL_TTL = Duration.ofMinutes(10);
    private static final Duration NEARBY_CATEGORY_TTL = Duration.ofMinutes(30);

    @Transactional(readOnly = true)
    public NearbyPlacePageResponse findNearby(double lat, double lng, double radius,
                                                             Long cursor, Double cursorDistance, Integer size) {
        // 캐시 키 생성: 좌표 소수점 3자리 반올림 및 반경 정수형 변환
        String latRounded = String.format("%.3f", lat);
        String lngRounded = String.format("%.3f", lng);
        double radiusRounded = Math.round(radius);

        // 첫 페이지일 경우에만 캐시 조회 (cursor 없음)
        boolean isFirstPage = (cursor == null);
        String cacheKey = String.format("nearby:%s:%s:%.0f:%d", latRounded, lngRounded, radiusRounded, size);
        
        if (isFirstPage) {
            String cachedData = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cachedData != null) {
                try {
                    log.debug("Redis Cache Hit");
                    return objectMapper.readValue(cachedData, new TypeReference<NearbyPlacePageResponse>() {});
                } catch (Exception e) {
                    log.error("Redis Cache parsing error", e);
                }
            }
        }

        log.info("Redis Cache Miss or Paging: Fetching from DB");

        int safeSize = (size != null) ? size : 10;

        // DB 단에서 공간 조회, 커서 페이징 로직 및 LIMIT(safeSize + 1)을 모두 수행
        List<NearbyPlaceResponse> pagedPlaces = placeQueryRepository.findNearbyPlaces(
                lat, lng, radius, cursor, cursorDistance, safeSize
        );

        boolean hasNext = false;

        if (pagedPlaces.size() > safeSize) {
            hasNext = true;
            pagedPlaces.remove(safeSize); // 초과 조회한 마지막 요소 제거
        }

        NearbyPlacePageResponse response = new NearbyPlacePageResponse(pagedPlaces, hasNext);

        // 첫 페이지 결과 캐싱 (TTL 5분)
        if (isFirstPage && !pagedPlaces.isEmpty()) {
            try {
                String json = objectMapper.writeValueAsString(response);
                stringRedisTemplate.opsForValue().set(cacheKey, json, Duration.ofMinutes(5));
            } catch (Exception e) {
                log.error("Redis Cache writing error", e);
            }
        }

        return response;
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
        String cacheKey = PlaceRedisConstants.PLACE_DETAIL_CACHE_PREFIX + placeId;

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
            String val = stringRedisTemplate.opsForValue().get(PlaceRedisConstants.PLACE_LIKE_COUNT_PREFIX + placeId);
            return (val != null) ? Integer.parseInt(val) : dbFallback;
        } catch (Exception e) {
            log.warn("Redis likeCount 조회 실패, DB 값 사용: placeId={}", placeId, e);
            return dbFallback;
        }
    }

    private void incrementViewCount(Long placeId) {
        try {
            stringRedisTemplate.opsForValue().increment(PlaceRedisConstants.PLACE_VIEW_COUNT_PREFIX + placeId);
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
    /**
     * 관광지 목록 조회 (PHASE 8-2)
     *
     * <p>카테고리/지역 필터와 커서 기반 페이지네이션을 지원합니다.
     * 필터 조합이 너무 다양하여 Redis 캐싱 효율이 낮으므로 캐싱은 미적용합니다.
     * (인기 목록은 추천 API의 Redis ZSet 방식을 사용합니다.)
     *
     * @param request 목록 조회 요청 DTO (category, region, cursor, size)
     * @return 관광지 목록 응답 (items, hasNext, nextCursor)
     */
    @Transactional(readOnly = true)
    public PlaceListResponse findList(PlaceListRequest request) {
        // Repository에서 limit(size+1)을 처리하므로, 여기서는 요청한 size만 넘깁니다.
        List<PlaceListResponse.PlaceListItem> items = placeQueryRepository.findByFilter(
                request.category(),
                request.region(),
                request.cursor(),
                request.cursorRating(),   // 복합 커서: rating도 함께 전달
                request.size()
        );

        boolean hasNext = items.size() > request.size();
        if (hasNext) {
            // subList는 원본의 뷰(view)를 반환하므로 독립된 리스트로 복사하여 안전성 확보
            items = new java.util.ArrayList<>(items.subList(0, request.size()));
        }

        // 다음 페이지 복합 커서: 현재 페이지 마지막 아이템의 ID와 rating
        Long nextCursorId = null;
        Float nextCursorRating = null;
        if (hasNext && !items.isEmpty()) {
            PlaceListResponse.PlaceListItem last = items.get(items.size() - 1);
            nextCursorId = last.id();
            nextCursorRating = last.rating();
        }

        return new PlaceListResponse(items, hasNext, nextCursorId, nextCursorRating);
    }

    /**
     * KAN-232: 관광지 주변 장소(맛집, 카페, 숙박) 카테고리 검색 연동
     * 카카오 로컬 API 활용
     * 응답은 Redis에 30분간 캐싱됨 (CacheConfig: nearby-category 설정)
     */
    public NearbyCategoryPlacesResponse findNearbyCategoryPlaces(Long placeId, NearbyCategory category, int radius) {
        Place place = placeRepository.findByIdAndStatus(placeId, PlaceStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND));
        
        int normalizedRadius = normalizeRadius(radius);
        String cacheKey = "nearby-category::" + placeId + ":" + category.name() + ":" + normalizedRadius;
        
        try {
            String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cachedJson != null) {
                return objectMapper.readValue(cachedJson, NearbyCategoryPlacesResponse.class);
            }
        } catch (Exception e) {
            log.warn("Redis 캐시 조회 실패, API 직접 호출로 fallback: {}", cacheKey, e);
        }
        
        
        KakaoCategoryResponse kakaoResponse = kakaoLocalApiClient.searchByCategory(
                new Coord(place.getLat(), place.getLng()), category.getCode(), normalizedRadius);

        if (kakaoResponse == null || kakaoResponse.documents() == null) {
            log.error("Kakao 카테고리 API 비정상 응답: documents is null");
            throw new BusinessException(ErrorCode.MAP_SERVICE_UNAVAILABLE);
        }

        boolean hasNext = false;
        if (kakaoResponse.meta() != null) {
            hasNext = !kakaoResponse.meta().isEnd();
        }

        List<NearbyCategoryPlacesResponse.NearbyCategoryItem> items;
        try {
            items = kakaoResponse.documents().stream()
                    .map(doc -> new NearbyCategoryPlacesResponse.NearbyCategoryItem(
                            doc.id(),
                            doc.placeName(),
                            doc.categoryName(),
                            doc.phone(),
                            doc.addressName(),
                            doc.roadAddressName(),
                            Double.parseDouble(doc.y()), // y is lat
                            Double.parseDouble(doc.x()), // x is lng
                            doc.placeUrl(),
                            doc.distance() != null && !doc.distance().isBlank() ? Integer.parseInt(doc.distance()) : 0
                    )).toList();
        } catch (NumberFormatException e) {
            log.error("Kakao 카테고리 응답 파싱 실패", e);
            throw new BusinessException(ErrorCode.MAP_SERVICE_UNAVAILABLE);
        }

        NearbyCategoryPlacesResponse response = new NearbyCategoryPlacesResponse(items, hasNext);
        
        try {
            String responseJson = objectMapper.writeValueAsString(response);
            stringRedisTemplate.opsForValue().set(cacheKey, responseJson, NEARBY_CATEGORY_TTL);
        } catch (Exception e) {
            log.warn("Redis 캐시 저장 실패: {}", cacheKey, e);
        }
        
        return response;
    }

    public int normalizeRadius(int radius) {
        if (radius <= 500) return 500;
        if (radius <= 1000) return 1000;
        if (radius <= 3000) return 3000;
        if (radius <= 5000) return 5000;
        if (radius <= 10000) return 10000;
        return 20000;
    }
}
