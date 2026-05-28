package com.chunbaetour.domain.shop.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 정산 API 권한 회귀 테스트.
 * 비로그인 401, 역할 불일치 403 검증 (서비스 로직 실행 전 Spring Security 차단).
 */
@SpringBootTest
@AutoConfigureMockMvc
class SettlementControllerTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenIssuer tokenIssuer;

    // ===== MERCHANT endpoint =====

    @Test
    @DisplayName("정산 신청 — 비로그인 401 AUTH_006")
    void requestSettlement_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/merchants/me/settlements"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_006"));
    }

    @Test
    @DisplayName("정산 신청 — USER 토큰 403 AUTH_007")
    void requestSettlement_userRole_returns403() throws Exception {
        String token = tokenIssuer.issueAccess(1L, Role.USER, "user@test.com");
        mockMvc.perform(post("/api/v1/merchants/me/settlements")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_007"));
    }

    @Test
    @DisplayName("내 정산 목록 — 비로그인 401 AUTH_006")
    void getMySettlements_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/merchants/me/settlements"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_006"));
    }

    @Test
    @DisplayName("내 정산 목록 — USER 토큰 403 AUTH_007")
    void getMySettlements_userRole_returns403() throws Exception {
        String token = tokenIssuer.issueAccess(1L, Role.USER, "user@test.com");
        mockMvc.perform(get("/api/v1/merchants/me/settlements")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_007"));
    }

    // ===== ADMIN endpoint =====

    @Test
    @DisplayName("정산 승인 — 비로그인 401 AUTH_006")
    void approveSettlement_unauthenticated_returns401() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/settlements/1/approve"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_006"));
    }

    @Test
    @DisplayName("정산 승인 — MERCHANT 토큰 403 AUTH_007")
    void approveSettlement_merchantRole_returns403() throws Exception {
        String token = tokenIssuer.issueAccess(1L, Role.MERCHANT, "merchant@test.com");
        mockMvc.perform(patch("/api/v1/admin/settlements/1/approve")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_007"));
    }

    @Test
    @DisplayName("정산 거절 — 비로그인 401 AUTH_006")
    void rejectSettlement_unauthenticated_returns401() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/settlements/1/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"거절 사유\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_006"));
    }

    @Test
    @DisplayName("정산 거절 — MERCHANT 토큰 403 AUTH_007")
    void rejectSettlement_merchantRole_returns403() throws Exception {
        String token = tokenIssuer.issueAccess(1L, Role.MERCHANT, "merchant@test.com");
        mockMvc.perform(patch("/api/v1/admin/settlements/1/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"거절 사유\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_007"));
    }
}
