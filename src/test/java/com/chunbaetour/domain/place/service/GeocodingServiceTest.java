package com.chunbaetour.domain.place.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.place.client.KakaoLocalApiClient;
import com.chunbaetour.domain.place.dto.KakaoAddressResponse;
import com.chunbaetour.domain.place.dto.response.GeocodingResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

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

    private GeocodingService geocodingService;

    private static final String QUERY = "서울 종로구 사직로 161";
    private static final String CACHE_KEY = "geocoding::" + QUERY;

    @BeforeEach
    void setUp() {
        geocodingService = new GeocodingService(kakaoLocalApiClient, stringRedisTemplate, new ObjectMapper());
        given(stringRedisTemplate.opsForValue()).willReturn(valueOps);
    }

    @Test
    @DisplayName("캐시 미스 — 카카오 API 호출 후 좌표 반환 및 Redis 캐시 저장")
    void geocode_cacheMiss_callsKakaoAndCaches() {
        // given: 캐시 없음
        given(valueOps.get(CACHE_KEY)).willReturn(null);

        KakaoAddressResponse.Document doc = new KakaoAddressResponse.Document(
                "서울 종로구 사직로 161", "ROAD_ADDR",
                "126.97700000", "37.57960000",
                null,
                new KakaoAddressResponse.RoadAddress("서울 종로구 사직로 161", "서울", "종로구", "", "사직로", "경복궁", "126.97700000", "37.57960000")
        );
        given(kakaoLocalApiClient.searchAddress(QUERY))
                .willReturn(new KakaoAddressResponse(List.of(doc), null));

        // when
        GeocodingResponse result = geocodingService.geocode(QUERY);

        // then
        assertThat(result.addressName()).isEqualTo("서울 종로구 사직로 161");
        assertThat(result.lat()).isEqualByComparingTo("37.57960000");
        assertThat(result.lng()).isEqualByComparingTo("126.97700000");

        // 카카오 API 1회 호출, Redis 캐시 저장 1회
        then(kakaoLocalApiClient).should().searchAddress(QUERY);
        then(valueOps).should().set(eq(CACHE_KEY), anyString(), any());
    }

    @Test
    @DisplayName("캐시 히트 — 카카오 API 호출 없이 Redis에서 즉시 반환")
    void geocode_cacheHit_returnsFromCacheWithoutKakaoCall() throws Exception {
        // given: 캐시에 JSON 저장되어 있음
        GeocodingResponse cached = new GeocodingResponse("서울 종로구 사직로 161",
                new java.math.BigDecimal("37.57960000"),
                new java.math.BigDecimal("126.97700000"));
        String cachedJson = new ObjectMapper().writeValueAsString(cached);
        given(valueOps.get(CACHE_KEY)).willReturn(cachedJson);

        // when
        GeocodingResponse result = geocodingService.geocode(QUERY);

        // then: 카카오 API 호출 없음
        then(kakaoLocalApiClient).should(never()).searchAddress(any());
        assertThat(result.lat()).isEqualByComparingTo("37.57960000");
    }

    @Test
    @DisplayName("카카오 응답 documents가 비어있으면 GEOCODING_RESULT_NOT_FOUND 예외")
    void geocode_emptyDocuments_throwsGeocodingResultNotFound() {
        // given
        given(valueOps.get(CACHE_KEY)).willReturn(null);
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
    void geocode_nullResponse_throwsGeocodingResultNotFound() {
        // given
        given(valueOps.get(CACHE_KEY)).willReturn(null);
        given(kakaoLocalApiClient.searchAddress(QUERY)).willReturn(null);

        // when & then
        assertThatThrownBy(() -> geocodingService.geocode(QUERY))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.GEOCODING_RESULT_NOT_FOUND);
    }

    @Test
    @DisplayName("카카오 응답 좌표 파싱 실패(빈 문자열)시 GEOCODING_RESULT_NOT_FOUND 예외")
    void geocode_invalidCoordinates_throwsGeocodingResultNotFound() {
        // given
        given(valueOps.get(CACHE_KEY)).willReturn(null);
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
}
