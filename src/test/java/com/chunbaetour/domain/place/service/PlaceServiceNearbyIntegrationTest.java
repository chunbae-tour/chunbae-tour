package com.chunbaetour.domain.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.client.KakaoLocalApiClient;
import com.chunbaetour.domain.place.dto.Coord;
import com.chunbaetour.domain.place.dto.KakaoCategoryResponse;
import com.chunbaetour.domain.place.repository.PlaceRepository;
import com.chunbaetour.domain.place.type.NearbyCategory;
import com.chunbaetour.domain.place.type.PlaceCategory;
import com.chunbaetour.domain.place.type.PlaceStatus;
import com.chunbaetour.domain.support.AbstractIntegrationTest;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class PlaceServiceNearbyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PlaceService placeService;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    @MockitoBean
    private KakaoLocalApiClient kakaoLocalApiClient;

    @AfterEach
    void cleanup() {
        placeRepository.deleteAll();
        redisTemplate.delete(redisTemplate.keys("nearby-category*"));
    }

    private Place createPlace(String name, PlaceStatus status) {
        Place place = Place.builder()
                .name(name)
                .category(PlaceCategory.TOURIST_SPOT)
                .description("설명")
                .address("주소")
                .lat(new BigDecimal("33.0"))
                .lng(new BigDecimal("126.0"))
                .build();
        
        if (status == PlaceStatus.HIDDEN) {
            place.hide();
        } else if (status == PlaceStatus.DELETED) {
            place.delete();
        }
        
        return placeRepository.save(place);
    }

    @Test
    @DisplayName("캐시 버킷 검증: 501m와 1000m 반경 요청이 정규화되어 카카오 API는 한 번만 호출된다 (캐시 히트)")
    void testRadiusNormalizationAndCacheHit() {
        Place place = createPlace("테스트 관광지", PlaceStatus.ACTIVE);
        KakaoCategoryResponse mockResponse = new KakaoCategoryResponse(List.of(), null);
        
        when(kakaoLocalApiClient.searchByCategory(any(Coord.class), eq(NearbyCategory.CAFE.getCode()), eq(1000)))
                .thenReturn(mockResponse);

        // 첫 호출 (반경 501 -> 정규화 1000)
        placeService.findNearbyCategoryPlaces(place.getId(), NearbyCategory.CAFE, 501);
        // 두 번째 호출 (반경 800 -> 정규화 1000, 캐시 히트되어야 함)
        placeService.findNearbyCategoryPlaces(place.getId(), NearbyCategory.CAFE, 800);
        // 세 번째 호출 (반경 1000 -> 정규화 1000, 캐시 히트되어야 함)
        placeService.findNearbyCategoryPlaces(place.getId(), NearbyCategory.CAFE, 1000);

        // 카카오 API 클라이언트는 1000 버킷에 대해 1번만 호출되었는지 검증
        verify(kakaoLocalApiClient, times(1)).searchByCategory(any(Coord.class), eq(NearbyCategory.CAFE.getCode()), eq(1000));
    }

    @Test
    @DisplayName("숨김/삭제 처리된 관광지의 주변 장소를 조회하면 PLACE_NOT_FOUND 예외가 발생한다")
    void testHiddenOrDeletedPlaceThrowsException() {
        Place hiddenPlace = createPlace("숨김 관광지", PlaceStatus.HIDDEN);
        Place deletedPlace = createPlace("삭제 관광지", PlaceStatus.DELETED);

        assertThatThrownBy(() -> placeService.findNearbyCategoryPlaces(hiddenPlace.getId(), NearbyCategory.CAFE, 500))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PLACE_NOT_FOUND);

        assertThatThrownBy(() -> placeService.findNearbyCategoryPlaces(deletedPlace.getId(), NearbyCategory.CAFE, 500))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PLACE_NOT_FOUND);
        
        verify(kakaoLocalApiClient, times(0)).searchByCategory(any(), any(), anyInt());
    }

    @Test
    @DisplayName("카카오 API 호출 시 에러가 발생하면 MAP_SERVICE_UNAVAILABLE 로 래핑된다")
    void testKakaoApiErrorWrapping() {
        Place place = createPlace("장애 테스트", PlaceStatus.ACTIVE);
        
        when(kakaoLocalApiClient.searchByCategory(any(Coord.class), eq(NearbyCategory.RESTAURANT.getCode()), eq(500)))
                .thenThrow(new BusinessException(ErrorCode.MAP_SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> placeService.findNearbyCategoryPlaces(place.getId(), NearbyCategory.RESTAURANT, 500))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.MAP_SERVICE_UNAVAILABLE);
    }
}
