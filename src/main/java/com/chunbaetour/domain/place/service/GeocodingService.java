package com.chunbaetour.domain.place.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.place.client.KakaoLocalApiClient;
import com.chunbaetour.domain.place.constant.PlaceRedisConstants;
import com.chunbaetour.domain.place.dto.KakaoAddressResponse;
import com.chunbaetour.domain.place.dto.response.GeocodingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

/**
 * 주소 → 좌표 변환(지오코딩) 서비스.
 *
 * <p>트랜잭션 없음: 외부 API(카카오) + Redis I/O만 수행하므로 DB 커넥션 불필요.
 * 동일 주소에 대한 반복 카카오 API 호출을 Redis 캐싱(24h)으로 방어한다.
 *
 * <p>캐시 키 안전성: 사용자 입력(query)을 SHA-256으로 해시해 캐시 키로 사용한다.
 * 특수문자·공백·개인정보가 Redis 키에 그대로 노출되는 것을 방지한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeocodingService {

    private static final Duration GEOCODING_TTL = Duration.ofHours(24);

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
        String cacheKey = buildCacheKey(query);

        // 1. Redis 캐시 조회
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, GeocodingResponse.class);
            } catch (Exception e) {
                // query는 개인정보가 될 수 있으므로 로그에 미포함
                log.warn("[Geocoding] 캐시 역직렬화 실패, 카카오 API 재조회. keyHash={}", cacheKey);
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
            // 카카오가 빈 문자열이나 비정상 좌표를 반환한 경우 — query 미포함(개인정보 보호)
            log.warn("[Geocoding] 카카오 응답 좌표 파싱 실패 (x={}, y={})", doc.x(), doc.y());
            throw new BusinessException(ErrorCode.GEOCODING_RESULT_NOT_FOUND);
        }

        // 4. 주소명 결정: 도로명 주소 우선, 없으면 지번 주소, 최후엔 원본 addressName
        String addressName = doc.addressName();
        if (doc.roadAddress() != null && doc.roadAddress().addressName() != null) {
            addressName = doc.roadAddress().addressName();
        } else if (doc.address() != null && doc.address().addressName() != null) {
            addressName = doc.address().addressName();
        }

        GeocodingResponse result = new GeocodingResponse(addressName, lat, lng);

        // 5. Redis 캐싱 (best-effort — Redis 장애 시 warn 후 계속)
        try {
            stringRedisTemplate.opsForValue().set(
                    cacheKey, objectMapper.writeValueAsString(result), GEOCODING_TTL);
        } catch (Exception e) {
            log.warn("[Geocoding] 캐시 저장 실패 (best-effort). keyHash={}", cacheKey);
        }

        return result;
    }

    /**
     * 사용자 입력(query)을 SHA-256 해시로 변환한 캐시 키를 생성한다.
     *
     * <p>query를 그대로 키로 쓰면 특수문자·개인정보·초장문이 Redis 키에 노출된다.
     * 해시 사용으로 키 길이 고정(64자) + 개인정보 보호를 동시에 달성한다.
     */
    private String buildCacheKey(String query) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(query.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return PlaceRedisConstants.GEOCODING_CACHE_PREFIX + hex;
        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 Java 표준 알고리즘이므로 실제로는 발생하지 않음
            log.warn("[Geocoding] SHA-256 해시 실패 — query 원문을 키로 사용 (fallback)");
            return PlaceRedisConstants.GEOCODING_CACHE_PREFIX + query;
        }
    }
}
