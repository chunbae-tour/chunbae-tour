package com.chunbaetour.domain.shop.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.auth.Role;
import com.chunbaetour.domain.auth.jwt.TokenIssuer;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 가게 수익 지갑 API 권한 회귀 테스트.
 * 비로그인 401, USER 역할 403 검증 (서비스 로직 실행 전 Spring Security 차단).
 *
 * @AfterEach 미포함 이유: Spring Security 필터에서 차단되므로 서비스·DB 레이어에 도달하지 않음.
 * 토큰은 TokenIssuer가 JWT를 인메모리 생성하므로 DB seed 없음 → 테스트 간 격리 보장.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ShopWalletControllerTest extends AbstractIntegrationTest {

    private static final String ENDPOINT = "/api/v1/merchants/me/shops/1/wallet";

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenIssuer tokenIssuer;

    @Test
    @DisplayName("가게 수익 지갑 조회 — 비로그인 401 AUTH_006")
    void getShopWallet_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_006"));
    }

    @Test
    @DisplayName("가게 수익 지갑 조회 — USER 토큰 403 AUTH_007")
    void getShopWallet_userRole_returns403() throws Exception {
        String token = tokenIssuer.issueAccess(1L, Role.USER, "user@test.com");
        mockMvc.perform(get(ENDPOINT)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_007"));
    }
}
