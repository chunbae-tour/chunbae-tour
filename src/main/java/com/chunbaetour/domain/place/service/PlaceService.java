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
@Transactional(readOnly = true)
public class PlaceService {

    private final PlaceRepository placeRepository;
    private final PlaceQueryRepository placeQueryRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final String PLACE_DETAIL_CACHE_PREFIX = "place:";
    private static final String PLACE_VIEW_COUNT_PREFIX  = "place:view:";
    private static final Duration PLACE_DETAIL_TTL       = Duration.ofMinutes(10);

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
     *
     * @param placeId 조회할 관광지 ID
     * @return PlaceDetailResponse (비로그인 시 isLiked = false)
     */
    public PlaceDetailResponse findById(Long placeId) {
        String cacheKey = PLACE_DETAIL_CACHE_PREFIX + placeId;

        // 1. Redis 캐시 조회
        String cachedData = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cachedData != null) {
            try {
                log.debug("Place Detail Cache Hit: placeId={}", placeId);
                PlaceCacheDto cached = objectMapper.readValue(cachedData, PlaceCacheDto.class);
                incrementViewCount(placeId);
                return PlaceDetailResponse.of(cached, false); // PHASE 3에서 사용자 로그인 여부 판별 로직 추가 예정
            } catch (Exception e) {
                log.error("Place Detail Cache parsing error: placeId={}", placeId, e);
            }
        }

        // 2. DB 조회
        log.debug("Place Detail Cache Miss: Fetching from DB, placeId={}", placeId);
        Place place = placeRepository.findById(placeId)
                .map(p -> {
                    if (p.getStatus() != PlaceStatus.ACTIVE) {
                        log.debug("Place is not ACTIVE: placeId={}, status={}", placeId, p.getStatus());
                        throw new BusinessException(ErrorCode.PLACE_NOT_FOUND);
                    }
                    return p;
                })
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND));

        // 3. imageUrls JSON 파싱
        List<String> imageUrlList = parseImageUrls(place.getImageUrls());

        PlaceCacheDto cacheDto = PlaceCacheDto.of(place, imageUrlList);
        PlaceDetailResponse response = PlaceDetailResponse.of(cacheDto, false);

        // 4. 캐싱 (TTL 10분)
        try {
            String json = objectMapper.writeValueAsString(cacheDto);
            stringRedisTemplate.opsForValue().set(cacheKey, json, PLACE_DETAIL_TTL);
        } catch (Exception e) {
            log.error("Place Detail Cache writing error: placeId={}", placeId, e);
        }

        // 5. 조회수 증가 (best-effort)
        incrementViewCount(placeId);

        return response;
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

