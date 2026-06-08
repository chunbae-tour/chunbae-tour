package com.chunbaetour.domain.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.mock;

import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.client.TourApiPlaceDetailClient;
import com.chunbaetour.domain.place.repository.PlaceRepository;
import com.chunbaetour.domain.place.type.PlaceSource;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class PlaceDetailEnrichmentServiceTest {

    @Mock
    private TourApiPlaceDetailClient detailClient;
    @Mock
    private PlaceRepository placeRepository;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private PlaceDetailEnrichmentService service;

    // 상세 수집 대상 조건을 만족하는 Place 목 (API_FETCH + externalId 있음 + description null)
    private Place enrichTargetPlace() {
        Place place = mock(Place.class);
        given(place.getSource()).willReturn(PlaceSource.API_FETCH);
        given(place.getExternalId()).willReturn("123456");
        given(place.getDescription()).willReturn(null);
        return place;
    }

    @Test
    @DisplayName("Redis 장애로 락 조회가 실패하면 외부 상세 API를 호출하지 않고 원본 Place를 반환한다(API 한도 보호)")
    void skipEnrichWhenRedisDown() {
        Place place = enrichTargetPlace();
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .willThrow(new RuntimeException("redis down"));

        Place result = service.enrichIfNeeded(place);

        // 원본 그대로 반환 + 외부 API 미호출(동시요청 폭주로 인한 일일 한도 소진 방지)
        assertThat(result).isSameAs(place);
        verify(detailClient, never()).fetchDetail(anyString());
    }

    @Test
    @DisplayName("락 미획득(다른 요청이 수집 중)이면 외부 상세 API를 호출하지 않고 원본 Place를 반환한다")
    void skipEnrichWhenLockNotAcquired() {
        Place place = enrichTargetPlace();
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .willReturn(false);

        Place result = service.enrichIfNeeded(place);

        assertThat(result).isSameAs(place);
        verify(detailClient, never()).fetchDetail(anyString());
    }

    @Test
    @DisplayName("이미 상세가 채워진(description != null) Place는 수집 대상이 아니므로 Redis·외부 API를 건드리지 않는다")
    void skipWhenAlreadyEnriched() {
        Place place = mock(Place.class);
        given(place.getSource()).willReturn(PlaceSource.API_FETCH);
        given(place.getExternalId()).willReturn("123456");
        given(place.getDescription()).willReturn("이미 채워진 설명");

        Place result = service.enrichIfNeeded(place);

        assertThat(result).isSameAs(place);
        verifyNoInteractions(redisTemplate);
        verifyNoInteractions(detailClient);
    }
}
