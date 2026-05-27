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
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.dao.DataAccessException;
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
        
        List<RecommendPlaceResponse> cachedResponses = getFromCache(key);
        if (!cachedResponses.isEmpty()) {
            return cachedResponses;
        }

        List<Place> popularPlaces = getFromDb();
        saveToCache(key, popularPlaces);

        return popularPlaces.stream()
                .map(RecommendPlaceResponse::from)
                .toList();
    }

    private List<RecommendPlaceResponse> getFromCache(String key) {
        Set<String> cachedPlaceIds = null;
        try {
            cachedPlaceIds = stringRedisTemplate.opsForZSet().reverseRange(key, 0, 9);
        } catch (Exception e) {
            log.error("Redis 인기 추천 조회 실패. DB Fallback 진행", e);
        }

        if (cachedPlaceIds == null || cachedPlaceIds.isEmpty()) {
            return Collections.emptyList();
        }

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
                .toList();
        
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, Place> placeMap = placeRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Place::getId, Function.identity()));
        
        return ids.stream()
                .filter(placeMap::containsKey)
                .map(id -> RecommendPlaceResponse.from(placeMap.get(id)))
                .toList();
    }

    private List<Place> getFromDb() {
        Pageable top10 = PageRequest.of(0, 10);
        return placeRepository.findTopPopularPlaces(PlaceRedisConstants.POPULAR_LIKE_WEIGHT, PlaceRedisConstants.POPULAR_VIEW_WEIGHT, top10);
    }

    private void saveToCache(String key, List<Place> popularPlaces) {
        if (popularPlaces.isEmpty()) {
            return;
        }
        
        try {
            Set<TypedTuple<String>> tuples = new HashSet<>();
            popularPlaces.forEach(place -> {
                double score = (place.getLikeCount() * PlaceRedisConstants.POPULAR_LIKE_WEIGHT) + 
                               (place.getViewCount() * PlaceRedisConstants.POPULAR_VIEW_WEIGHT);
                tuples.add(new DefaultTypedTuple<>(String.valueOf(place.getId()), score));
            });
            
            stringRedisTemplate.executePipelined(new SessionCallback<Object>() {
                @Override
                public Object execute(RedisOperations operations) throws DataAccessException {
                    operations.delete(key);
                    operations.opsForZSet().add(key, tuples);
                    operations.expire(key, PlaceRedisConstants.RECOMMEND_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
                    return null;
                }
            });
        } catch (Exception e) {
            log.error("Redis 인기 추천 데이터 캐싱 실패", e);
        }
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
        // TODO: Redis GeoSearch 로직 (Phase 5 마커 데이터 동기화와 고도화)
        // 시니어 아키텍트 리뷰 반영: DB 쿼리 내 ORDER BY RAND()는 치명적인 성능 저하 유발.
        // 넉넉하게 반경 내 최대 50건을 가져온 뒤 애플리케이션 단에서 셔플 후 슬라이스(Limit) 샘플링 처리
        int fetchSize = Math.max(50, limit);
        List<Place> nearbyPlaces = new java.util.ArrayList<>(
            placeRepository.findNearbyPlacesWithinRadius(lat, lng, radius, fetchSize)
        );
        
        Collections.shuffle(nearbyPlaces);
        
        return nearbyPlaces.stream()
                .limit(limit)
                .map(place -> RecommendPlaceResponse.fromWithDistance(
                        place, 
                        calculateDistance(lat, lng, place.getLat().doubleValue(), place.getLng().doubleValue())
                ))
                .toList();
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // 지구 반경 (km)
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c * 1000; // 미터 단위로 변환
    }

    /**
     * 카테고리별 추천 (평점 내림차순)
     */
    @Transactional(readOnly = true)
    public List<RecommendPlaceResponse> getCategoryRecommendations(PlaceCategory category) {
        if (category == null) {
            throw new IllegalArgumentException("카테고리는 null일 수 없습니다.");
        }
        
        Pageable top10 = PageRequest.of(0, 10);
        return placeRepository.findTopByCategory(category, top10).stream()
                .map(RecommendPlaceResponse::from)
                .toList();
    }
}
