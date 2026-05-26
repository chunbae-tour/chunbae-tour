package com.chunbaetour.domain.place.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.constant.PlaceRedisConstants;
import com.chunbaetour.domain.place.dto.response.RecommendPlaceResponse;
import com.chunbaetour.domain.place.repository.PlaceRepository;
import com.chunbaetour.domain.place.type.PlaceCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.data.redis.core.DefaultTypedTuple;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendService {

    private final PlaceRepository placeRepository;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 인기 관광지 추천 (Top 10)
     * Redis ZSet (recommend:popular) 확인 후, 없으면 DB Fallback 쿼리로 계산
     */
    @Transactional(readOnly = true)
    public List<RecommendPlaceResponse> getPopularRecommendations() {
        String key = PlaceRedisConstants.RECOMMEND_POPULAR_KEY;
        Set<String> cachedPlaceIds = null;
        
        try {
            cachedPlaceIds = stringRedisTemplate.opsForZSet().reverseRange(key, 0, 9);
        } catch (Exception e) {
            log.error("Redis 인기 추천 조회 실패. DB Fallback 진행", e);
        }

        // Redis 캐시 히트
        if (cachedPlaceIds != null && !cachedPlaceIds.isEmpty()) {
            List<Long> ids = cachedPlaceIds.stream()
                    .map(idStr -> {
                        try {
                            return Long.valueOf(idStr);
                        } catch (NumberFormatException e) {
                            log.warn("캐시된 관광지 ID 파싱 실패: {}", idStr);
                            return null;
                        }
                    })
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());
            
            if (!ids.isEmpty()) {
                Map<Long, Place> placeMap = placeRepository.findAllById(ids).stream()
                        .collect(Collectors.toMap(Place::getId, Function.identity()));
            
                // Redis 정렬 순서 유지
                return ids.stream()
                        .filter(placeMap::containsKey)
                        .map(id -> RecommendPlaceResponse.from(placeMap.get(id)))
                        .collect(Collectors.toList());
            }
        }

        // DB Fallback 쿼리
        Pageable top10 = PageRequest.of(0, 10);
        List<Place> popularPlaces = placeRepository.findTopPopularPlaces(top10);

        // Redis 캐싱 (ZADD Bulk Insert) - 시니어 아키텍트 리뷰: forEach 단건 삽입 대신 한 번의 네트워크 I/O로 최적화
        if (!popularPlaces.isEmpty()) {
            try {
                Set<TypedTuple<String>> tuples = new HashSet<>();
                popularPlaces.forEach(place -> {
                    double score = (place.getLikeCount() * 0.7) + (place.getViewCount() * 0.3);
                    tuples.add(new DefaultTypedTuple<>(String.valueOf(place.getId()), score));
                });
                stringRedisTemplate.opsForZSet().add(key, tuples);
                stringRedisTemplate.expire(key, PlaceRedisConstants.RECOMMEND_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            } catch (Exception e) {
                log.error("Redis 인기 추천 데이터 캐싱 실패", e);
            }
        }

        return popularPlaces.stream()
                .map(RecommendPlaceResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 반경 내 주변 관광지 추천 (랜덤 샘플링)
     * Redis GEOSEARCH 확인 후, 없으면 DB Fallback 쿼리 (Haversine)
     *
     * @param lat 위도
     * @param lng 경도
     * @param radius 반경 (km)
     * @param limit 샘플링할 개수
     */
    @Transactional(readOnly = true)
    public List<RecommendPlaceResponse> getNearbyRecommendations(double lat, double lng, double radius, int limit) {
        // TODO: Redis GeoSearch 로직 (Phase 5 마커 데이터 동기화 시 고도화)
        // 시니어 아키텍트 리뷰 반영: DB 쿼리 내 ORDER BY RAND()는 치명적인 성능 저하 유발.
        // 넉넉하게 반경 내 최대 50건을 가져온 뒤 애플리케이션 단에서 셔플 및 잘라내어(Limit) 샘플링 처리
        int fetchSize = 50;
        List<Place> nearbyPlaces = placeRepository.findNearbyPlacesWithinRadius(lat, lng, radius, fetchSize);
        
        Collections.shuffle(nearbyPlaces);
        
        return nearbyPlaces.stream()
                .limit(limit)
                .map(RecommendPlaceResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 카테고리별 추천 (평점 내림차순)
     */
    @Transactional(readOnly = true)
    public List<RecommendPlaceResponse> getCategoryRecommendations(PlaceCategory category) {
        Pageable top10 = PageRequest.of(0, 10);
        return placeRepository.findTopByCategory(category, top10).stream()
                .map(RecommendPlaceResponse::from)
                .collect(Collectors.toList());
    }
}
