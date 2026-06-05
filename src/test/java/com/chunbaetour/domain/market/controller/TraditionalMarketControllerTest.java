package com.chunbaetour.domain.market.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.market.repository.TraditionalMarketRepository;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * GET /api/v1/traditional-markets/nearby 컨트롤러 테스트.
 * 공개 API 인증 없이 200, 파라미터 유효성, 응답 JSON 구조 검증.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TraditionalMarketControllerTest extends AbstractIntegrationTest {

    private static final String NEARBY_ENDPOINT = "/api/v1/traditional-markets/nearby";

    @Autowired private MockMvc mockMvc;
    @Autowired private TraditionalMarketRepository marketRepository;

    @AfterEach
    void cleanup() {
        marketRepository.deleteAll();
    }

    @Test
    @DisplayName("nearby — 토큰 없이 빈 목록 200 (공개 API 비인증 허용 검증)")
    void nearby_withoutToken_returns200() throws Exception {
        mockMvc.perform(get(NEARBY_ENDPOINT)
                        .param("lat", "37.5665")
                        .param("lng", "126.9780"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.markets").isArray())
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    @DisplayName("nearby — lat 누락 시 400 COMMON_002")
    void nearby_missingLat_returns400() throws Exception {
        mockMvc.perform(get(NEARBY_ENDPOINT)
                        .param("lng", "126.9780"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("nearby — lat 범위 초과(91) 시 400 COMMON_002")
    void nearby_latOutOfRange_returns400() throws Exception {
        mockMvc.perform(get(NEARBY_ENDPOINT)
                        .param("lat", "91")
                        .param("lng", "126.9780"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));
    }

    @Test
    @DisplayName("nearby — radius 최솟값 미만(99) 시 400 COMMON_002")
    void nearby_radiusTooSmall_returns400() throws Exception {
        mockMvc.perform(get(NEARBY_ENDPOINT)
                        .param("lat", "37.5665")
                        .param("lng", "126.9780")
                        .param("radius", "99"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));
    }

    @Test
    @DisplayName("nearby — size 최댓값 초과(101) 시 400 COMMON_002")
    void nearby_sizeTooLarge_returns400() throws Exception {
        mockMvc.perform(get(NEARBY_ENDPOINT)
                        .param("lat", "37.5665")
                        .param("lng", "126.9780")
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));
    }
}
