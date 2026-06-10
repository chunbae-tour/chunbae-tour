package com.chunbaetour.domain.place.repository;

import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.dto.response.MapMarkerResponse;
import com.chunbaetour.domain.place.type.PlaceCategory;
import com.chunbaetour.domain.place.type.PlaceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.chunbaetour.domain.support.AbstractIntegrationTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PlaceQueryRepositoryMapMarkerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PlaceQueryRepository placeQueryRepository;

    @Autowired
    private PlaceRepository placeRepository;

    @BeforeEach
    void setUp() {
        placeRepository.deleteAll();

        // 1. 정상적으로 Bounding Box(SW: 33.0, 126.0 / NE: 34.0, 127.0) 내에 있는 마커
        placeRepository.save(createPlace("마커1 (제주 내부)", 33.5, 126.5, PlaceStatus.ACTIVE));
        placeRepository.save(createPlace("마커2 (제주 내부 가장자리)", 33.1, 126.1, PlaceStatus.ACTIVE));

        // 2. Bounding Box 밖에 있는 마커 (서울)
        placeRepository.save(createPlace("마커3 (서울)", 37.5, 127.0, PlaceStatus.ACTIVE));

        // 3. Bounding Box 안에 있지만 비활성화된 마커
        placeRepository.save(createPlace("마커4 (제주 내부 삭제됨)", 33.6, 126.6, PlaceStatus.DELETED));
    }

    private Place createPlace(String name, double lat, double lng, PlaceStatus status) {
        Place place = Place.builder()
                .name(name)
                .address("테스트 주소")
                .category(PlaceCategory.TOURIST_SPOT)
                .phone("010")
                .thumbnailUrl("http://thumb")
                .lat(BigDecimal.valueOf(lat))
                .lng(BigDecimal.valueOf(lng))
                .build();
        if (status == PlaceStatus.DELETED) {
            place.delete();
        }
        return place;
    }

    @Test
    void findMarkersInBoundingBox_바운딩박스_내부의_활성마커만_가져온다() {
        // given
        double swLat = 33.0;
        double swLng = 126.0;
        double neLat = 34.0;
        double neLng = 127.0;

        // when
        List<MapMarkerResponse> markers = placeQueryRepository.findMarkersInBoundingBox(swLat, swLng, neLat, neLng);

        // then
        assertThat(markers).hasSize(2);
        assertThat(markers)
                .extracting("name")
                .containsExactlyInAnyOrder("마커1 (제주 내부)", "마커2 (제주 내부 가장자리)");
    }
}
