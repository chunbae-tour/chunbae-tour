package com.chunbaetour.domain.admin.dashboard;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.auth.AccountSeedFactory;
import com.chunbaetour.domain.auth.AccountStatus;
import com.chunbaetour.domain.auth.Role;
import com.chunbaetour.domain.auth.dto.LoginRequest;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 운영자 대시보드 API 통합 테스트 (KAN-181, Admin Epic KAN-177 S03).
 *
 * <p>검증 시나리오
 * <ul>
 *   <li>admin 토큰 → 200 + 사용자 카운트 3종 필드 노출</li>
 *   <li>USER 토큰 403(AUTH_007) / 미인증 401(AUTH_006) — {@code /api/v1/admin/**} 경로 가드</li>
 * </ul>
 *
 * <p>Redis 캐시는 graceful degradation이므로 통합 환경(Redis 미가용 가능)에서도 DB 폴백으로 항상 200.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminDashboardControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "Pa$$w0rd1!";

    // AdminDashboardService 캐시 전역 키 — 테스트 간 오염 방지를 위해 계정과 함께 매번 제거.
    private static final String CACHE_KEY = "admin:dashboard:summary";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AccountRepository accountRepository;
    @Autowired private AccountSeedFactory seedFactory;
    @Autowired private StringRedisTemplate redisTemplate;

    @AfterEach
    void cleanup() {
        accountRepository.deleteAll();
        redisTemplate.delete(CACHE_KEY);
    }

    @Test
    @DisplayName("admin 토큰 → 200 + 카운트 3종 값 단정 (admin+ACTIVE+SUSPENDED)")
    void getDashboard_returns_200_with_counts() throws Exception {
        String adminToken = adminToken();
        seedFactory.seed("u1@test.com", PASSWORD, "유저1", Role.USER, AccountStatus.ACTIVE);
        seedFactory.seed("s1@test.com", PASSWORD, "정지자", Role.USER, AccountStatus.SUSPENDED);

        // admin + u1(ACTIVE) + s1(SUSPENDED) = 전체>=2(실 3, 탈퇴 제외), 정지>=1.
        mockMvc.perform(get("/api/v1/admin/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.totalUsers").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.suspendedUsers").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.newUsersToday").exists())
                // S06: 가게/인증/상인신청 카운트 3종 노출 (값은 시드 무관, 존재 + 음수 아님만 검증).
                .andExpect(jsonPath("$.data.totalShops").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.data.pendingCertifications").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.data.pendingMerchantApplications").value(greaterThanOrEqualTo(0)));
    }

    @Test
    @DisplayName("USER 토큰 → 403 AUTH_007")
    void user_token_forbidden() throws Exception {
        seedFactory.seed("user@test.com", PASSWORD, "유저", Role.USER, AccountStatus.ACTIVE);
        String userToken = loginAndGetToken("/api/v1/users/auth/login", "user@test.com");

        mockMvc.perform(get("/api/v1/admin/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_007"));
    }

    @Test
    @DisplayName("미인증 → 401 AUTH_006")
    void unauthenticated_returns_401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_006"));
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────

    private String adminToken() throws Exception {
        seedFactory.seedAdmin("admin@test.com", PASSWORD, "관리자");
        return loginAndGetToken("/api/v1/admin/auth/login", "admin@test.com");
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
