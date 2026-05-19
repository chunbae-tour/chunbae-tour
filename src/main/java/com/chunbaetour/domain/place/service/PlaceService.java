package com.chunbaetour.domain.place.service;

import com.chunbaetour.domain.place.dto.response.NearbyPlaceResponse;
import com.chunbaetour.domain.place.repository.PlaceQueryRepository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceService {

    private final PlaceQueryRepository placeQueryRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public List<NearbyPlaceResponse> findNearby(double lat, double lng, double radius, Long cursor, Double cursorDistance, int size) {
        // 캐시 키 생성: 좌표 소수점 3자리 반올림 및 반경 정수형 변환
        String latRounded = String.format("%.3f", lat);
        String lngRounded = String.format("%.3f", lng);
        double radiusRounded = Math.round(radius);
        String cacheKey = String.format("nearby:%s:%s:%.0f:%d", latRounded, lngRounded, radiusRounded, size);

        // 첫 페이지일 경우에만 캐시 조회
        if (cursor == null) {
            String cachedData = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cachedData != null) {
                try {
                    log.info("Redis Cache Hit: {}", cacheKey);
                    return objectMapper.readValue(cachedData, new TypeReference<>() {});
                } catch (Exception e) {
                    log.error("Redis Cache parsing error", e);
                }
            }
        }

        // DB 쿼리 (Haversine)
        log.info("Redis Cache Miss or Paging: Fetching from DB");
        List<NearbyPlaceResponse> places = placeQueryRepository.findNearbyPlaces(lat, lng, radiusRounded, cursor, cursorDistance, size);

        // 첫 페이지 결과 캐싱 (TTL 5분)
        if (cursor == null && !places.isEmpty()) {
            try {
                String json = objectMapper.writeValueAsString(places);
                stringRedisTemplate.opsForValue().set(cacheKey, json, Duration.ofMinutes(5));
            } catch (Exception e) {
                log.error("Redis Cache writing error", e);
            }
        }

        return places;
    }
}

