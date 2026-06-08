package com.chunbaetour.domain.place.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.place.client.KakaoLocalApiClient;
import com.chunbaetour.domain.place.dto.KakaoRegionResponse;
import com.chunbaetour.domain.place.dto.response.RegionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ReverseGeocodingServiceTest {

    private ReverseGeocodingService reverseGeocodingService;

    @Mock
    private KakaoLocalApiClient kakaoLocalApiClient;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock rLock;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    private static final double LAT = 33.450701;
    private static final double LNG = 126.570667;
    // 캐시 키는 소수점 3자리
    private static final String CACHE_KEY = String.format(Locale.ROOT, "region:%.3f:%.3f", LAT, LNG);

    @BeforeEach
    void setUp() throws Exception {
        reverseGeocodingService = new ReverseGeocodingService(
                kakaoLocalApiClient,
                stringRedisTemplate,
                redissonClient,
                objectMapper
        );

        // 빠른 경로 캐시 조회를 위한 기본 Mock (대부분 캐시 Miss)
        org.mockito.Mockito.lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);

        // 락 관련 기본 Mock
        org.mockito.Mockito.lenient().when(redissonClient.getLock(anyString())).thenReturn(rLock);
        org.mockito.Mockito.lenient().when(rLock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
        org.mockito.Mockito.lenient().when(rLock.isHeldByCurrentThread()).thenReturn(true);
    }

    @Test
    @DisplayName("캐시 Miss -> 카카오 API 호출 후 캐시 저장 및 반환")
    void reverseGeocode_cacheMiss_callsKakaoApiAndCaches() throws Exception {
        // given
        given(valueOps.get(CACHE_KEY)).willReturn(null);

        KakaoRegionResponse.Document doc = new KakaoRegionResponse.Document(
                "B", "제주특별자치도 제주시 영평동", "제주특별자치도", "제주시", "영평동", ""
        );
        KakaoRegionResponse mockResponse = new KakaoRegionResponse(
                new KakaoRegionResponse.Meta(1), List.of(doc)
        );
        given(kakaoLocalApiClient.getRegionCode(LAT, LNG)).willReturn(mockResponse);

        // when
        RegionResponse result = reverseGeocodingService.reverseGeocode(LAT, LNG);

        // then
        assertThat(result.depth1()).isEqualTo("제주특별자치도");
        assertThat(result.depth2()).isEqualTo("제주시");
        assertThat(result.depth3()).isEqualTo("영평동");
        assertThat(result.fullAddress()).isEqualTo("제주특별자치도 제주시 영평동");

        then(kakaoLocalApiClient).should().getRegionCode(LAT, LNG);
        then(valueOps).should().set(eq(CACHE_KEY), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("캐시 Hit -> 카카오 API 호출 없이 캐시 반환")
    void reverseGeocode_cacheHit_returnsCachedResult() throws Exception {
        // given
        RegionResponse cached = new RegionResponse("제주특별자치도", "제주시", "영평동", "제주특별자치도 제주시 영평동");
        String cachedJson = objectMapper.writeValueAsString(cached);

        given(valueOps.get(CACHE_KEY)).willReturn(cachedJson);

        // when
        RegionResponse result = reverseGeocodingService.reverseGeocode(LAT, LNG);

        // then
        assertThat(result.depth1()).isEqualTo("제주특별자치도");
        assertThat(result.fullAddress()).isEqualTo("제주특별자치도 제주시 영평동");

        then(kakaoLocalApiClient).should(never()).getRegionCode(anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("락 획득 실패 시 Fallback(캐시 재조회)가 성공하면 결과 반환")
    void reverseGeocode_lockAcquisitionFails_fallbackSucceeds() throws Exception {
        // given
        RegionResponse cached = new RegionResponse("제주특별자치도", "서귀포시", "안덕면", "제주특별자치도 서귀포시 안덕면");
        String cachedJson = objectMapper.writeValueAsString(cached);

        // 첫 조회는 null, 두 번째 조회(Fallback)는 값 반환
        given(valueOps.get(CACHE_KEY))
                .willReturn(null)
                .willReturn(cachedJson);
        
        given(rLock.tryLock(anyLong(), anyLong(), any())).willReturn(false);

        // when
        RegionResponse result = reverseGeocodingService.reverseGeocode(LAT, LNG);

        // then
        assertThat(result.depth2()).isEqualTo("서귀포시");
        then(kakaoLocalApiClient).should(never()).getRegionCode(anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("락 획득 실패 및 캐시 미스 시 외부 API 직접 조회(Fallback)")
    void reverseGeocode_lockAcquisitionFailsAndCacheMiss_fallbackToApi() throws Exception {
        // given
        // 첫 조회 null, 두 번째 조회(Fallback)도 null
        given(valueOps.get(CACHE_KEY))
                .willReturn(null)
                .willReturn(null);
        
        given(rLock.tryLock(anyLong(), anyLong(), any())).willReturn(false);

        KakaoRegionResponse.Document doc = new KakaoRegionResponse.Document(
                "B", "제주특별자치도 서귀포시 안덕면", "제주특별자치도", "서귀포시", "안덕면", ""
        );
        KakaoRegionResponse mockResponse = new KakaoRegionResponse(
                new KakaoRegionResponse.Meta(1), List.of(doc)
        );
        given(kakaoLocalApiClient.getRegionCode(LAT, LNG)).willReturn(mockResponse);

        // when
        RegionResponse result = reverseGeocodingService.reverseGeocode(LAT, LNG);

        // then
        assertThat(result.depth2()).isEqualTo("서귀포시");
        assertThat(result.depth3()).isEqualTo("안덕면");
        then(kakaoLocalApiClient).should().getRegionCode(LAT, LNG);
    }

    @Test
    @DisplayName("카카오 API 응답이 없으면 GEOCODING_RESULT_NOT_FOUND 예외")
    void reverseGeocode_kakaoApiReturnsEmpty_throwsException() {
        // given
        given(valueOps.get(CACHE_KEY)).willReturn(null);
        KakaoRegionResponse emptyResponse = new KakaoRegionResponse(new KakaoRegionResponse.Meta(0), List.of());
        given(kakaoLocalApiClient.getRegionCode(LAT, LNG)).willReturn(emptyResponse);

        // when & then
        assertThatThrownBy(() -> reverseGeocodingService.reverseGeocode(LAT, LNG))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.GEOCODING_RESULT_NOT_FOUND);
    }
}
