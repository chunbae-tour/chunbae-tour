package com.chunbaetour.domain.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.client.TourApiPlaceDetail;
import com.chunbaetour.domain.place.client.TourApiPlaceDetailClient;
import com.chunbaetour.domain.place.repository.PlaceRepository;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class PlaceDetailEnrichmentIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PlaceService placeService;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private TourApiPlaceDetailClient detailClient;

    @AfterEach
    void cleanup() {
        placeRepository.deleteAll();
        // 상세 캐시/락 키 제거 — 테스트 간 캐시 히트로 enrich 경로가 안 타는 것 방지
        Set<String> keys = redisTemplate.keys("place*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        Set<String> locks = redisTemplate.keys("lock:place*");
        if (locks != null && !locks.isEmpty()) {
            redisTemplate.delete(locks);
        }
    }

    private Place saveApiPlace(String externalId) {
        return placeRepository.saveAndFlush(Place.createFromApi(
                externalId, "테스트관광지", "서울특별시 종로구",
                BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.0), null, null, "서울특별시", "종로구"));
    }

    @Test
    @DisplayName("API 수집 관광지 첫 상세조회 시 detail을 수집해 설명·운영시간·휴무일을 저장한다")
    void enrichOnFirstView() {
        Place place = saveApiPlace("100");
        given(detailClient.fetchDetail("100"))
                .willReturn(new TourApiPlaceDetail("멋진 관광지 설명", "09:00-18:00", "매주 월요일"));

        placeService.findById(place.getId(), null);

        Place reloaded = placeRepository.findById(place.getId()).orElseThrow();
        assertThat(reloaded.getDescription()).isEqualTo("멋진 관광지 설명");
        assertThat(reloaded.getOperatingHours()).isEqualTo("09:00-18:00");
        assertThat(reloaded.getClosedDays()).isEqualTo("매주 월요일");
        verify(detailClient).fetchDetail("100");
    }

    @Test
    @DisplayName("이미 상세가 채워진 관광지는 detail API를 호출하지 않는다")
    void skipWhenAlreadyEnriched() {
        Place place = saveApiPlace("200");
        place.applyApiDetail("기존 설명", "10:00-20:00", null); // description != null → 수집 완료 상태
        placeRepository.saveAndFlush(place);

        placeService.findById(place.getId(), null);

        verify(detailClient, never()).fetchDetail(any());
    }

    @Test
    @DisplayName("detail 수집이 실패해도 상세 조회는 깨지지 않고 원본을 반환한다")
    void failureDoesNotBreakDetailView() {
        Place place = saveApiPlace("300");
        given(detailClient.fetchDetail("300")).willThrow(new RuntimeException("external API down"));

        // 예외가 밖으로 새지 않아야 함
        placeService.findById(place.getId(), null);

        Place reloaded = placeRepository.findById(place.getId()).orElseThrow();
        assertThat(reloaded.getDescription()).isNull(); // 저장 안 됨 → 다음 조회 때 재시도 가능
    }
}
