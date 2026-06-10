package com.chunbaetour.domain.place.controller;

import com.chunbaetour.domain.place.dto.response.MapMarkerPageResponse;
import com.chunbaetour.domain.place.dto.response.MapMarkerResponse;
import com.chunbaetour.domain.place.service.PlaceLikeService;
import com.chunbaetour.domain.place.service.PlaceService;
import com.chunbaetour.domain.place.service.RecommendService;
import com.chunbaetour.domain.place.type.PlaceCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PlaceControllerMapMarkerTest {

    private MockMvc mockMvc;

    @Mock
    private PlaceService placeService;

    @Mock
    private PlaceLikeService placeLikeService;

    @Mock
    private RecommendService recommendService;

    @InjectMocks
    private PlaceController placeController;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(placeController)
                .setValidator(validator)
                .setControllerAdvice(new com.chunbaetour.domain.common.error.GlobalExceptionHandler()) // 예외 처리
                .build();
    }

    @Test
    @DisplayName("정상적인 위경도 파라미터가 주어지면 마커 목록을 반환한다")
    void getMapMarkers_Success() throws Exception {
        // given
        List<MapMarkerResponse> markers = List.of(
                new MapMarkerResponse(1L, "테스트 관광지", PlaceCategory.TOURIST_SPOT,
                        BigDecimal.valueOf(33.5), BigDecimal.valueOf(126.5), "http://thumb.jpg")
        );
        MapMarkerPageResponse mockResponse = new MapMarkerPageResponse(markers, false, 500);
        given(placeService.getMapMarkers(any())).willReturn(mockResponse);

        // when & then
        mockMvc.perform(get("/api/v1/places/map-markers")
                        .param("swLat", "33.0")
                        .param("swLng", "126.0")
                        .param("neLat", "34.0")
                        .param("neLng", "127.0")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.markers[0].id").value(1L))
                .andExpect(jsonPath("$.data.markers[0].name").value("테스트 관광지"))
                .andExpect(jsonPath("$.data.truncated").value(false));
    }

    @Test
    @DisplayName("위경도 파라미터가 누락되면 400 에러를 반환한다")
    void getMapMarkers_MissingParam() throws Exception {
        mockMvc.perform(get("/api/v1/places/map-markers")
                        .param("swLat", "33.0")
                        // swLng 누락
                        .param("neLat", "34.0")
                        .param("neLng", "127.0")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("남서쪽 위경도가 북동쪽 위경도보다 큰 역전 박스일 경우 400 에러를 반환한다")
    void getMapMarkers_ReversedBoundingBox() throws Exception {
        mockMvc.perform(get("/api/v1/places/map-markers")
                        .param("swLat", "34.0") // neLat(33.0) 보다 큼
                        .param("swLng", "127.0") // neLng(126.0) 보다 큼
                        .param("neLat", "33.0")
                        .param("neLng", "126.0")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("남서쪽 좌표와 북동쪽 좌표가 같아 면적이 0인 경우 400 에러를 반환한다")
    void getMapMarkers_DegenerateBoundingBox() throws Exception {
        mockMvc.perform(get("/api/v1/places/map-markers")
                        .param("swLat", "33.0")
                        .param("swLng", "126.0")
                        .param("neLat", "33.0")
                        .param("neLng", "127.0")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("지도 뷰포트 범위(Span)가 2도를 초과할 경우 400 에러를 반환한다")
    void getMapMarkers_TooLargeSpan() throws Exception {
        mockMvc.perform(get("/api/v1/places/map-markers")
                        .param("swLat", "33.0")
                        .param("swLng", "125.0")
                        .param("neLat", "36.0") // Span 3도 (> 2도)
                        .param("neLng", "128.0") // Span 3도 (> 2도)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}
