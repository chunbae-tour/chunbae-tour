package com.chunbaetour.domain.admin.market;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.auth.AccountSeedFactory;
import com.chunbaetour.domain.auth.AccountStatus;
import com.chunbaetour.domain.auth.Role;
import com.chunbaetour.domain.auth.dto.LoginRequest;
import com.chunbaetour.domain.market.entity.TraditionalMarket;
import com.chunbaetour.domain.market.repository.TraditionalMarketRepository;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 운영자 전통시장 조회 API 통합 테스트 (KAN-308).
 *
 * <p>목록(keyword/sido 필터·cursor 페이징)·상세 조회 + 접근제어. 시장은 조회 전용(수정/삭제 없음).
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminMarketControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String ENDPOINT = "/api/v1/admin/traditional-markets";
    private static final String PASSWORD = "Pa$$w0rd1!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AccountRepository accountRepository;
    @Autowired private AccountSeedFactory seedFactory;
    @Autowired private TraditionalMarketRepository traditionalMarketRepository;

    @AfterEach
    void cleanup() {
        traditionalMarketRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    @DisplayName("GET 목록 → 200 + 페이징 계약(hasNext/nextCursor) (KAN-308)")
    void getMarkets_returns200_pagingContract() throws Exception {
        String token = adminToken();
        seedMarket("광장시장", "서울특별시");
        seedMarket("남대문시장", "서울특별시");

        mockMvc.perform(get(ENDPOINT).param("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.nextCursor").isNotEmpty())
                .andExpect(jsonPath("$.data.size").value(1));
    }

    @Test
    @DisplayName("GET 목록 keyword 필터 → 시장명 부분일치만")
    void getMarkets_keywordFilter() throws Exception {
        String token = adminToken();
        seedMarket("광장시장", "서울특별시");
        seedMarket("남대문시장", "서울특별시");

        mockMvc.perform(get(ENDPOINT).param("keyword", "광장")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("광장시장"));
    }

    @Test
    @DisplayName("GET 목록 sido 필터 → 해당 시도만")
    void getMarkets_sidoFilter() throws Exception {
        String token = adminToken();
        seedMarket("광장시장", "서울특별시");
        seedMarket("부산자갈치시장", "부산광역시");

        mockMvc.perform(get(ENDPOINT).param("sido", "부산광역시")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[*].sido", Matchers.everyItem(Matchers.is("부산광역시"))));
    }

    @Test
    @DisplayName("GET 목록 size=0 → 400 COMMON_002")
    void getMarkets_size0_returns400() throws Exception {
        String token = adminToken();
        mockMvc.perform(get(ENDPOINT).param("size", "0")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));
    }

    @Test
    @DisplayName("GET 목록 size=101 → 400 COMMON_002")
    void getMarkets_size101_returns400() throws Exception {
        String token = adminToken();
        mockMvc.perform(get(ENDPOINT).param("size", "101")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));
    }

    @Test
    @DisplayName("GET 목록 유효하지 않은 cursor → 400 COMMON_008")
    void getMarkets_invalidCursor_returns400() throws Exception {
        String token = adminToken();
        mockMvc.perform(get(ENDPOINT).param("cursor", "!!!")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_008"));
    }

    @Test
    @DisplayName("GET 상세 → 200 + 전체 필드")
    void getMarket_returns200() throws Exception {
        String token = adminToken();
        TraditionalMarket market = seedMarket("광장시장", "서울특별시");

        mockMvc.perform(get(ENDPOINT + "/" + market.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(market.getId()))
                .andExpect(jsonPath("$.data.name").value("광장시장"))
                .andExpect(jsonPath("$.data.address").value("서울특별시 어딘가 1-1"))
                .andExpect(jsonPath("$.data.lat").value(37.5701))
                .andExpect(jsonPath("$.data.lng").value(126.9997))
                .andExpect(jsonPath("$.data.sigungu").value("종로구"))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.sido").value("서울특별시"))
                .andExpect(jsonPath("$.data.marketType").value("상설장"));
    }

    @Test
    @DisplayName("GET 상세 미존재 → 404 PLACE_002 (MARKET_NOT_FOUND)")
    void getMarket_notFound_returns404() throws Exception {
        String token = adminToken();
        mockMvc.perform(get(ENDPOINT + "/999999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_002"));
    }

    @Test
    @DisplayName("GET 상세 marketId=0 → 400 COMMON_002 (@Positive 위반)")
    void getMarket_nonPositiveId_returns400() throws Exception {
        String token = adminToken();
        mockMvc.perform(get(ENDPOINT + "/0")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));
    }

    @Test
    @DisplayName("GET 상세 비인증 → 401")
    void getMarket_noToken_returns401() throws Exception {
        TraditionalMarket market = seedMarket("광장시장", "서울특별시");
        mockMvc.perform(get(ENDPOINT + "/" + market.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET 상세 USER 토큰 → 403 AUTH_007")
    void getMarket_userToken_forbidden() throws Exception {
        TraditionalMarket market = seedMarket("광장시장", "서울특별시");
        seedFactory.seed("user-market-detail@test.com", PASSWORD, "유저", Role.USER, AccountStatus.ACTIVE);
        String userToken = loginAndGetToken("/api/v1/users/auth/login", "user-market-detail@test.com");

        mockMvc.perform(get(ENDPOINT + "/" + market.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_007"));
    }

    @Test
    @DisplayName("GET 목록 — 데이터 없으면 빈 content + hasNext=false + nextCursor 없음")
    void getMarkets_empty_returnsEmptyPage() throws Exception {
        String token = adminToken();
        mockMvc.perform(get(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(0))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.nextCursor").value(Matchers.nullValue()));
    }

    @Test
    @DisplayName("GET 목록 비인증 → 401")
    void getMarkets_noToken_returns401() throws Exception {
        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET 목록 USER 토큰 → 403 AUTH_007")
    void getMarkets_userToken_forbidden() throws Exception {
        seedFactory.seed("user-market@test.com", PASSWORD, "유저", Role.USER, AccountStatus.ACTIVE);
        String userToken = loginAndGetToken("/api/v1/users/auth/login", "user-market@test.com");

        mockMvc.perform(get(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_007"));
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────

    private TraditionalMarket seedMarket(String name, String sido) {
        return traditionalMarketRepository.save(TraditionalMarket.builder()
                .name(name).address(sido + " 어딘가 1-1")
                .lat(new BigDecimal("37.5701000")).lng(new BigDecimal("126.9997000"))
                .marketType("상설장").sido(sido).sigungu("종로구").build());
    }

    private String adminToken() throws Exception {
        seedFactory.seedAdmin("admin-market@test.com", PASSWORD, "관리자");
        return loginAndGetToken("/api/v1/admin/auth/login", "admin-market@test.com");
    }

    private String loginAndGetToken(String endpoint, String email) throws Exception {
        LoginRequest req = new LoginRequest(email, PASSWORD);
        MvcResult result = mockMvc.perform(post(endpoint)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("data").get("accessToken").asString();
    }
}
