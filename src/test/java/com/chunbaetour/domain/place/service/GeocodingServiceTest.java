package com.chunbaetour.domain.place.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.place.client.KakaoLocalApiClient;
import com.chunbaetour.domain.place.dto.KakaoAddressResponse;
import com.chunbaetour.domain.place.dto.response.GeocodingResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class GeocodingServiceTest {

    @Mock
    private KakaoLocalApiClient kakaoLocalApiClient;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    private GeocodingService geocodingService;

    private static final String QUERY = "서울 종로구 사직로 161";

    /** GeocodingService와 동일한 SHA-256 키 생성 로직 */
    private static String cacheKey(String query) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(query.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        return "geocoding::" + hex;
    }

    @BeforeEach
    void setUp() {
        geocodingService = new GeocodingService(kakaoLocalApiClient, stringRedisTemplate, objectMapper);
        org.mockito.Mockito.lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("캐시 미스 — 카카오 API 호출 후 좌표 반환 및 Redis 캐시 저장")
    void geocode_cacheMiss_callsKakaoAndCaches() throws Exception {
        // given: 캐시 없음
        String key = cacheKey(QUERY);
        given(valueOps.get(key)).willReturn(null);

        KakaoAddressResponse.Document doc = new KakaoAddressResponse.Document(
                "서울 종로구 사직로 161", "ROAD_ADDR",
                "126.97700000", "37.57960000",
                null,
                new KakaoAddressResponse.RoadAddress(
                        "서울 종로구 사직로 161", "서울", "종로구", "", "사직로", "경복궁",
                        "126.97700000", "37.57960000")
        );
        given(kakaoLocalApiClient.searchAddress(QUERY))
                .willReturn(new KakaoAddressResponse(List.of(doc), null));

        // when
        GeocodingResponse result = geocodingService.geocode(QUERY);

        // then: 좌표 정확성 검증
        assertThat(result.addressName()).isEqualTo("서울 종로구 사직로 161");
        assertThat(result.lat()).isEqualByComparingTo("37.57960000");
        assertThat(result.lng()).isEqualByComparingTo("126.97700000");

        // 카카오 API 1회 호출 + Redis 캐시 저장 확인
        then(kakaoLocalApiClient).should().searchAddress(QUERY);
        then(valueOps).should().set(eq(key), anyString(), any());
    }

    @Test
    @DisplayName("캐시 히트 — 카카오 API 호출 없이 Redis에서 즉시 반환")
    void geocode_cacheHit_returnsFromCacheWithoutKakaoCall() throws Exception {
        // given: 캐시에 JSON 저장되어 있음
        GeocodingResponse cached = new GeocodingResponse("서울 종로구 사직로 161",
                new BigDecimal("37.57960000"), new BigDecimal("126.97700000"));
        String cachedJson = objectMapper.writeValueAsString(cached);
        given(valueOps.get(cacheKey(QUERY))).willReturn(cachedJson);

        // when
        GeocodingResponse result = geocodingService.geocode(QUERY);

        // then: 카카오 API 미호출, 캐시 값 반환
        then(kakaoLocalApiClient).should(never()).searchAddress(any());
        assertThat(result.lat()).isEqualByComparingTo("37.57960000");
        assertThat(result.addressName()).isEqualTo("서울 종로구 사직로 161");
    }

    @Test
    @DisplayName("카카오 응답 documents가 비어있으면 GEOCODING_RESULT_NOT_FOUND 예외")
    void geocode_emptyDocuments_throwsGeocodingResultNotFound() throws Exception {
        // given
        given(valueOps.get(cacheKey(QUERY))).willReturn(null);
        given(kakaoLocalApiClient.searchAddress(QUERY))
                .willReturn(new KakaoAddressResponse(List.of(), null));

        // when & then
        assertThatThrownBy(() -> geocodingService.geocode(QUERY))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.GEOCODING_RESULT_NOT_FOUND);
    }

    @Test
    @DisplayName("카카오 응답이 null이면 GEOCODING_RESULT_NOT_FOUND 예외")
    void geocode_nullResponse_throwsGeocodingResultNotFound() throws Exception {
        // given
        given(valueOps.get(cacheKey(QUERY))).willReturn(null);
        given(kakaoLocalApiClient.searchAddress(QUERY)).willReturn(null);

        // when & then
        assertThatThrownBy(() -> geocodingService.geocode(QUERY))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.GEOCODING_RESULT_NOT_FOUND);
    }

    @Test
    @DisplayName("카카오 응답 좌표 파싱 실패(빈 문자열)시 GEOCODING_RESULT_NOT_FOUND 예외")
    void geocode_invalidCoordinates_throwsGeocodingResultNotFound() throws Exception {
        // given: 카카오가 빈 문자열로 x/y 반환하는 케이스
        given(valueOps.get(cacheKey(QUERY))).willReturn(null);
        KakaoAddressResponse.Document badDoc = new KakaoAddressResponse.Document(
                "서울", "ROAD_ADDR", "", "", null, null
        );
        given(kakaoLocalApiClient.searchAddress(QUERY))
                .willReturn(new KakaoAddressResponse(List.of(badDoc), null));

        // when & then
        assertThatThrownBy(() -> geocodingService.geocode(QUERY))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.GEOCODING_RESULT_NOT_FOUND);
    }

    @Test
    @DisplayName("카카오 API 장애(MAP_SERVICE_UNAVAILABLE) 시 예외가 그대로 전파됨")
    void geocode_kakaoApiError_propagatesMapServiceUnavailable() throws Exception {
        // given: 카카오 API가 MAP_SERVICE_UNAVAILABLE 던짐 (4xx/5xx/네트워크 오류)
        given(valueOps.get(cacheKey(QUERY))).willReturn(null);
        given(kakaoLocalApiClient.searchAddress(QUERY))
                .willThrow(new BusinessException(ErrorCode.MAP_SERVICE_UNAVAILABLE));

        // when & then: GeocodingService는 예외를 그대로 전파
        assertThatThrownBy(() -> geocodingService.geocode(QUERY))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MAP_SERVICE_UNAVAILABLE);

        // 장애 시 캐시 저장 미호출 확인
        then(valueOps).should(never()).set(any(), any(), any());
    }

    @Test
    @DisplayName("query가 null이거나 공백이면 GEOCODING_RESULT_NOT_FOUND 예외")
    void geocode_invalidQuery_throwsException() {
        assertThatThrownBy(() -> geocodingService.geocode(null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.GEOCODING_RESULT_NOT_FOUND);

        assertThatThrownBy(() -> geocodingService.geocode("   "))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.GEOCODING_RESULT_NOT_FOUND);
    }
}
