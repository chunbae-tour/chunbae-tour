package com.chunbaetour.domain.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.BDDMockito.given;

import com.chunbaetour.domain.place.dto.request.MapMarkerRequest;
import com.chunbaetour.domain.place.dto.response.MapMarkerPageResponse;
import com.chunbaetour.domain.place.dto.response.MapMarkerResponse;
import com.chunbaetour.domain.place.repository.PlaceQueryRepository;
import com.chunbaetour.domain.place.type.PlaceCategory;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlaceServiceMapMarkerTest {

    @Mock
    private PlaceQueryRepository placeQueryRepository;

    @InjectMocks
    private PlaceService placeService;

    @Test
    @DisplayName("DB에서 500개 이하가 조회되면 truncated는 false이고 원본 리스트를 반환한다")
    void getMapMarkers_NotTruncated() {
        // given
        List<MapMarkerResponse> repoResult = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            repoResult.add(new MapMarkerResponse((long) i, "관광지", PlaceCategory.TOURIST_SPOT,
                    BigDecimal.valueOf(33.0), BigDecimal.valueOf(126.0), ""));
        }
        given(placeQueryRepository.findMarkersInBoundingBox(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .willReturn(repoResult);
        
        MapMarkerRequest request = new MapMarkerRequest(
                BigDecimal.valueOf(33.0), BigDecimal.valueOf(126.0),
                BigDecimal.valueOf(34.0), BigDecimal.valueOf(127.0));

        // when
        MapMarkerPageResponse response = placeService.getMapMarkers(request);

        // then
        assertThat(response.markers()).hasSize(500);
        assertThat(response.truncated()).isFalse();
        assertThat(response.limit()).isEqualTo(500);
    }

    @Test
    @DisplayName("DB에서 501개가 조회되면 500개로 자르고 truncated는 true를 반환한다")
    void getMapMarkers_Truncated() {
        // given
        List<MapMarkerResponse> repoResult = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            repoResult.add(new MapMarkerResponse((long) i, "관광지", PlaceCategory.TOURIST_SPOT,
                    BigDecimal.valueOf(33.0), BigDecimal.valueOf(126.0), ""));
        }
        given(placeQueryRepository.findMarkersInBoundingBox(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .willReturn(repoResult);
        
        MapMarkerRequest request = new MapMarkerRequest(
                BigDecimal.valueOf(33.0), BigDecimal.valueOf(126.0),
                BigDecimal.valueOf(34.0), BigDecimal.valueOf(127.0));

        // when
        MapMarkerPageResponse response = placeService.getMapMarkers(request);

        // then
        assertThat(response.markers()).hasSize(500);
        assertThat(response.truncated()).isTrue();
        assertThat(response.limit()).isEqualTo(500);
    }
}
