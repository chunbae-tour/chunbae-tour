package com.chunbaetour.domain.place.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.common.error.GlobalExceptionHandler;
import com.chunbaetour.domain.place.service.PlaceReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@ExtendWith(MockitoExtension.class)
class PlaceReviewControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PlaceReviewService placeReviewService;

    @InjectMocks
    private PlaceReviewController placeReviewController;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(placeReviewController)
                .setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("리뷰 작성 요청 본문 검증 실패는 400 COMMON_002로 응답한다")
    void createReview_InvalidBody_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/places/{placeId}/reviews", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rating": 0,
                                  "content": "",
                                  "imageUrls": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));

        verify(placeReviewService, never()).createReview(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("리뷰 작성 JSON 파싱 실패는 400 COMMON_002로 응답한다")
    void createReview_MalformedJson_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/places/{placeId}/reviews", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rating": "bad",
                                  "content": "좋아요",
                                  "imageUrls": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));

        verify(placeReviewService, never()).createReview(anyLong(), anyLong(), any());
    }
}
