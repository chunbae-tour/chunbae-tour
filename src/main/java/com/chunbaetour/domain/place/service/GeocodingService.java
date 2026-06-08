package com.chunbaetour.domain.place.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.place.client.KakaoLocalApiClient;
import com.chunbaetour.domain.place.dto.KakaoAddressResponse;
import com.chunbaetour.domain.place.dto.response.GeocodingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * 주소 → 좌표 변환(지오코딩) 서비스.
 *
 * <p>트랜잭션 없음: 외부 API(카카오) + Redis I/O만 수행하므로 DB 커넥션 불필요.
 * 동일 주소에 대한 반복 카카오 API 호출을 Redis 캐싱(24h)으로 방어한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeocodingService {

    private static final Duration GEOCODING_TTL = Duration.ofHours(24);
    private static final String CACHE_PREFIX = "geocoding::";

    private final KakaoLocalApiClient kakaoLocalApiClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 주소 문자열을 위도·경도로 변환한다.
     *
     * @param query 변환할 주소 문자열 (도로명/지번 모두 허용)
     * @return 좌표 및 정제된 주소명
     * @throws BusinessException GEOCODING_RESULT_NOT_FOUND — 일치하는 좌표가 없을 때
     * @throws BusinessException MAP_SERVICE_UNAVAILABLE   — 카카오 API 장애 시
     */
    public GeocodingResponse geocode(String query) {
        String cacheKey = CACHE_PREFIX + query;

        // 1. Redis 캐시 조회
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, GeocodingResponse.class);
            } catch (Exception e) {
                log.warn("[Geocoding] 캐시 역직렬화 실패, 카카오 API 재조회. key={}", cacheKey, e);
            }
        }

        // 2. 카카오 주소 검색 API 호출
        KakaoAddressResponse response = kakaoLocalApiClient.searchAddress(query);

        if (response == null || response.documents() == null || response.documents().isEmpty()) {
            throw new BusinessException(ErrorCode.GEOCODING_RESULT_NOT_FOUND);
        }

        KakaoAddressResponse.Document doc = response.documents().get(0);

        // 3. 좌표 파싱 — x=경도, y=위도
        BigDecimal lat;
        BigDecimal lng;
        try {
            lat = new BigDecimal(doc.y());
            lng = new BigDecimal(doc.x());
        } catch (NumberFormatException e) {
            log.warn("[Geocoding] 카카오 응답 좌표 파싱 실패: x={}, y={}", doc.x(), doc.y());
            throw new BusinessException(ErrorCode.GEOCODING_RESULT_NOT_FOUND);
        }

        // 4. 주소명 결정: 도로명 주소 우선, 없으면 지번 주소
        String addressName = doc.addressName();
        if (doc.roadAddress() != null && doc.roadAddress().addressName() != null) {
            addressName = doc.roadAddress().addressName();
        } else if (doc.address() != null && doc.address().addressName() != null) {
            addressName = doc.address().addressName();
        }

        GeocodingResponse result = new GeocodingResponse(addressName, lat, lng);

        // 5. Redis 캐싱
        try {
            stringRedisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(result), GEOCODING_TTL);
        } catch (Exception e) {
            log.warn("[Geocoding] 캐시 저장 실패 (best-effort). key={}", cacheKey, e);
        }

        return result;
    }
}
