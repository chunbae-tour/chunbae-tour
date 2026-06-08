package com.chunbaetour.domain.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.client.TourApiPlaceDetail;
import com.chunbaetour.domain.place.client.TourApiPlaceDetailClient;
import com.chunbaetour.domain.place.repository.PlaceRepository;
import java.time.Duration;
import java.util.Optional;
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

    // 상세 수집 대상인 Place 목 (needsDetailEnrichment=true)
    private Place enrichTargetPlace() {
        Place place = mock(Place.class);
        given(place.needsDetailEnrichment()).willReturn(true);
        given(place.getExternalId()).willReturn("123456");
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
    @DisplayName("수집 대상이 아니면(needsDetailEnrichment=false) Redis·외부 API를 건드리지 않는다")
    void skipWhenNotTarget() {
        Place place = mock(Place.class);
        given(place.needsDetailEnrichment()).willReturn(false);

        Place result = service.enrichIfNeeded(place);

        assertThat(result).isSameAs(place);
        verifyNoInteractions(redisTemplate);
        verifyNoInteractions(detailClient);
    }

    @Test
    @DisplayName("applyDetail: overview가 있으면 상세를 반영한다")
    void applyDetailWithOverview() {
        Place place = mock(Place.class);
        given(place.needsDetailEnrichment()).willReturn(true);
        given(placeRepository.findById(1L)).willReturn(Optional.of(place));
        given(placeRepository.save(place)).willReturn(place);

        service.applyDetail(1L, new TourApiPlaceDetail("설명입니다", "10:00-18:00", "월요일"));

        verify(place).applyApiDetail("설명입니다", "10:00-18:00", "월요일");
        verify(place, never()).recordEmptyEnrichAttempt();
        verify(placeRepository).save(place);
    }

    @Test
    @DisplayName("applyDetail: 성공 응답이지만 overview가 비면 설명을 채우지 않고 재시도 횟수만 누적한다")
    void applyDetailWithEmptyOverview() {
        Place place = mock(Place.class);
        given(place.needsDetailEnrichment()).willReturn(true);
        given(placeRepository.findById(1L)).willReturn(Optional.of(place));
        given(placeRepository.save(place)).willReturn(place);

        service.applyDetail(1L, new TourApiPlaceDetail(null, null, null));

        verify(place).recordEmptyEnrichAttempt();
        verify(place, never()).applyApiDetail(anyString(), any(), any());
        verify(placeRepository).save(place);
    }

    @Test
    @DisplayName("applyDetail: 이미 수집 완료/한도 소진(needsDetailEnrichment=false)이면 아무 것도 하지 않는다")
    void applyDetailIdempotentWhenDone() {
        Place place = mock(Place.class);
        given(place.needsDetailEnrichment()).willReturn(false);
        given(placeRepository.findById(1L)).willReturn(Optional.of(place));

        service.applyDetail(1L, new TourApiPlaceDetail("설명", null, null));

        verify(place, never()).applyApiDetail(anyString(), any(), any());
        verify(place, never()).recordEmptyEnrichAttempt();
        verify(placeRepository, never()).save(any());
    }
}
