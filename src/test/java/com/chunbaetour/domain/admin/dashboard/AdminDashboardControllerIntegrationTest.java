package com.chunbaetour.domain.admin.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.admin.dashboard.dto.response.AdminDashboardResponse;
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

    // ── 캐시 JSON 직렬화 (프로덕션 ObjectMapper 빈으로 검증) ────────────────────

    @Test
    @DisplayName("캐시 호환: 구버전 3필드 JSON 역직렬화 → S06 3필드 null (실패 없음)")
    void cache_legacyThreeFieldJson_deserializes_newFieldsNull() throws Exception {
        // 배포 직후 Redis에 남은 구버전(3필드) 캐시 JSON을 프로덕션 빈이 역직렬화해도 깨지지 않아야 한다.
        String legacyJson = "{\"totalUsers\":42,\"newUsersToday\":7,\"suspendedUsers\":3}";

        AdminDashboardResponse response = objectMapper.readValue(legacyJson, AdminDashboardResponse.class);

        assertThat(response.totalUsers()).isEqualTo(42L);
        assertThat(response.newUsersToday()).isEqualTo(7L);
        assertThat(response.suspendedUsers()).isEqualTo(3L);
        // append + nullable이라 구버전 JSON에 없는 S06 필드는 null (역직렬화 실패 X).
        assertThat(response.totalShops()).isNull();
        assertThat(response.pendingCertifications()).isNull();
        assertThat(response.pendingMerchantApplications()).isNull();
    }

    @Test
    @DisplayName("캐시 라운드트립: 6필드 직렬화→역직렬화 동일성 (프로덕션 빈)")
    void cache_sixField_roundTrip() throws Exception {
        AdminDashboardResponse original = new AdminDashboardResponse(42L, 7L, 3L, 11L, 5L, 2L);

        String json = objectMapper.writeValueAsString(original);
        AdminDashboardResponse restored = objectMapper.readValue(json, AdminDashboardResponse.class);

        assertThat(restored).isEqualTo(original);
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
