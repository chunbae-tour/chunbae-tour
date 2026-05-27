package com.chunbaetour.domain.place.repository;

import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.type.PlaceCategory;
import com.chunbaetour.domain.place.type.PlaceStatus;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PlaceRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PlaceRepository placeRepository;

    @AfterEach
    void tearDown() {
        placeRepository.deleteAll();
    }

    @Test
    @DisplayName("findNearbyPlacesByCategory - 지정된 카테고리, 상태(ACTIVE), 자기 자신 제외, 거리순 정렬 확인")
    void findNearbyPlacesByCategory_Integration() {
        // given
        // 기준점 (위도: 37.5, 경도: 127.0)
        double baseLat = 37.5;
        double baseLng = 127.0;
        
        // 1. 기준 관광지 (제외 대상)
        Place basePlace = Place.builder()
                .name("기준 관광지")
                .category(PlaceCategory.TOURIST_SPOT)
                .lat(BigDecimal.valueOf(baseLat))
                .lng(BigDecimal.valueOf(baseLng))
                .build();
        placeRepository.save(basePlace);

        // 2. 가장 가까운 장소 (거리순 1위)
        Place nearPlace1 = Place.builder()
                .name("가까운 관광지 1")
                .category(PlaceCategory.TOURIST_SPOT)
                .lat(BigDecimal.valueOf(37.501))
                .lng(BigDecimal.valueOf(127.001))
                .build();
        placeRepository.save(nearPlace1);

        // 3. 조금 더 먼 장소 (거리순 2위)
        Place nearPlace2 = Place.builder()
                .name("가까운 관광지 2")
                .category(PlaceCategory.TOURIST_SPOT)
                .lat(BigDecimal.valueOf(37.502))
                .lng(BigDecimal.valueOf(127.002))
                .build();
        placeRepository.save(nearPlace2);

        // 4. 가까운데 카테고리가 다른 장소 (필터링 대상)
        Place differentCategoryPlace = Place.builder()
                .name("카테고리 다른 곳")
                .category(PlaceCategory.TRADITIONAL_MARKET)
                .lat(BigDecimal.valueOf(37.5005))
                .lng(BigDecimal.valueOf(127.0005))
                .build();
        placeRepository.save(differentCategoryPlace);

        // 5. 가까운데 비활성(HIDDEN)인 장소 (필터링 대상)
        Place hiddenPlace = Place.builder()
                .name("숨김 관광지")
                .category(PlaceCategory.TOURIST_SPOT)
                .lat(BigDecimal.valueOf(37.5001))
                .lng(BigDecimal.valueOf(127.0001))
                .build();
        hiddenPlace.hide();
        placeRepository.save(hiddenPlace);

        // when
        List<Place> result = placeRepository.findNearbyPlacesByCategory(
                baseLat, baseLng, PlaceCategory.TOURIST_SPOT.name(), basePlace.getId(), 5
        );

        // then
        assertThat(result).hasSize(2); // 기준장소 제외, 카테고리 다름 제외, 숨김 제외 -> 딱 2개만 나와야 함
        assertThat(result.get(0).getName()).isEqualTo("가까운 관광지 1"); // 거리순 정렬 확인
        assertThat(result.get(1).getName()).isEqualTo("가까운 관광지 2");
    }
}
