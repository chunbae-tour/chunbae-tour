package com.chunbaetour.domain.place.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.place.client.KakaoLocalApiClient;
import com.chunbaetour.domain.place.dto.KakaoRegionResponse;
import com.chunbaetour.domain.place.dto.response.RegionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReverseGeocodingService {

    private final KakaoLocalApiClient kakaoLocalApiClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    private static final long CACHE_TTL_HOURS = 24;
    private static final String CACHE_KEY_PREFIX = "region:";

    /**
     * 좌표(위경도)를 행정구역 주소로 변환 (소수점 3자리 캐싱)
     */
    public RegionResponse reverseGeocode(double lat, double lng) {
        // 1. 소수점 4자리(약 11m 정밀도) 반올림 처리 (캐시 키와 카카오 API 호출 모두에 적용하여 데이터 정합성 유지)
        // 행정동 경계 부근 오염을 최소화하기 위해 111m(3자리)에서 11m(4자리)로 정밀도 상향 (트레이드오프: 히트율 소폭 감소)
        double roundedLat = Math.round(lat * 10000.0) / 10000.0;
        double roundedLng = Math.round(lng * 10000.0) / 10000.0;

        String cacheKey = String.format(Locale.ROOT, "%s%.4f:%.4f", CACHE_KEY_PREFIX, roundedLat, roundedLng);

        // 2. 빠른 경로 (Fast path) 조회
        RegionResponse cached = getFromCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 3. Cache Stampede 방지용 Redisson 분산 락
        RLock lock;
        try {
            lock = redissonClient.getLock("lock:" + cacheKey);
            boolean isLocked = lock.tryLock(5, 10, TimeUnit.SECONDS);
            if (!isLocked) {
                // 락 획득 실패 시 Fallback 조회
                RegionResponse fallback = getFromCache(cacheKey);
                if (fallback != null) {
                    return fallback;
                }
                log.warn("[ReverseGeocoding] 락 획득 실패 및 캐시 미스, 카카오 API 강제 조회로 Fallback. key={}", cacheKey);
                // TODO: Redis 장애 시 N개 스레드가 동시에 카카오 API로 팬아웃될 수 있으므로, 추후 Rate-limit이나 Circuit Breaker 도입 고려
                return fetchFromKakaoAndCache(roundedLat, roundedLng, cacheKey);
            }
            try {
                // Double-checked locking
                RegionResponse doubleCheck = getFromCache(cacheKey);
                if (doubleCheck != null) {
                    return doubleCheck;
                }

                return fetchFromKakaoAndCache(roundedLat, roundedLng, cacheKey);
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.MAP_SERVICE_UNAVAILABLE);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[ReverseGeocoding] Redis 장애로 락 획득 예외 발생, 카카오 API 강제 조회. key={}", cacheKey);
            return fetchFromKakaoAndCache(roundedLat, roundedLng, cacheKey);
        }
    }

    private RegionResponse getFromCache(String cacheKey) {
        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return objectMapper.readValue(cached, RegionResponse.class);
            }
        } catch (Exception e) {
            log.warn("[ReverseGeocoding] 캐시 조회 실패, 카카오 API 재조회. key={}", cacheKey);
        }
        return null;
    }

    private RegionResponse fetchFromKakaoAndCache(double lat, double lng, String cacheKey) {
        KakaoRegionResponse response = kakaoLocalApiClient.getRegionCode(lat, lng);

        if (response == null || response.documents() == null || response.documents().isEmpty()) {
            throw new BusinessException(ErrorCode.GEOCODING_RESULT_NOT_FOUND);
        }

        // 일관된 주소 표현을 위해 '법정동(B)' 우선 선택, 없으면 0번째 사용
        KakaoRegionResponse.Document doc = response.documents().stream()
                .filter(d -> "B".equals(d.regionType()))
                .findFirst()
                .orElse(response.documents().get(0));
        
        String depth1 = doc.region1depthName() != null ? doc.region1depthName() : "";
        String depth2 = doc.region2depthName() != null ? doc.region2depthName() : "";
        String depth3 = doc.region3depthName() != null ? doc.region3depthName() : "";
        String fullAddress = doc.addressName() != null ? doc.addressName() : "";

        RegionResponse result = new RegionResponse(depth1, depth2, depth3, fullAddress);

        try {
            String json = objectMapper.writeValueAsString(result);
            stringRedisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("[ReverseGeocoding] 캐시 저장 실패, 로직은 정상 반환 진행. key={}", cacheKey, e);
        }

        return result;
    }
}
