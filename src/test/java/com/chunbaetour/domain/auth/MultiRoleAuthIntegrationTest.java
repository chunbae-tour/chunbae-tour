package com.chunbaetour.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.auth.dto.LoginRequest;
import com.chunbaetour.domain.auth.dto.SignupRequest;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
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
 * 페이지별 로그인 endpoint + URL 권한 매핑 end-to-end 검증.
 *
 * <p>PRD AC 핵심 시나리오:
 * <ul>
 *   <li>각 page의 로그인 endpoint는 매칭되는 role만 통과 — 미스매치 시 AUTH_007</li>
 *   <li>발급된 토큰은 매칭되는 test-auth-fixture endpoint만 통과 — 다른 page의 fixture는 AUTH_007.
 *       fixture는 {@link com.chunbaetour.domain.auth.support.TestAuthFixtureController}가 제공 (test scope).
 *       KAN-129 (Epic A S4) 임시 ping endpoint 제거 후 role 매핑 검증을 도메인 endpoint 의존 없이 수행하기 위함.</li>
 *   <li>reissue/logout은 페이지 무관 공통 endpoint ({@code /api/v1/auth/**})</li>
 * </ul>
 *
 * <p>MERCHANT/ADMIN 계정은 회원가입 흐름이 없으므로 {@link AccountSeedFactory}로 직접 시드한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MultiRoleAuthIntegrationTest extends AbstractIntegrationTest {

    private static final String COOKIE_NAME = "refreshToken";
    private static final String PASSWORD = "Pa$$w0rd1!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountSeedFactory seedFactory;

    @Autowired
    private StringRedisTemplate redis;

    @AfterEach
    void cleanup() {
        accountRepository.deleteAll();
        deleteByPrefix("auth:refresh:*");
        deleteByPrefix("auth:blacklist:*");
    }

    // ===== USER endpoint =====

    @Test
    void user_login_then_user_ping_returns_200() throws Exception {
        signupUser("user@example.com", "유저닉");
        String accessToken = login("/api/v1/users/auth/login", "user@example.com").accessToken();

        mockMvc.perform(get("/api/v1/users/test-auth-fixture")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void user_token_calling_merchants_me_ping_returns_AUTH_007() throws Exception {
        signupUser("user2@example.com", "유저닉2");
        String accessToken = login("/api/v1/users/auth/login", "user2@example.com").accessToken();

        mockMvc.perform(get("/api/v1/merchants/test-auth-fixture")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_007"));
    }

    @Test
    void user_token_calling_admin_me_ping_returns_AUTH_007() throws Exception {
        signupUser("user3@example.com", "유저닉3");
        String accessToken = login("/api/v1/users/auth/login", "user3@example.com").accessToken();

        mockMvc.perform(get("/api/v1/admin/test-auth-fixture")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_007"));
    }

    // ===== MERCHANT endpoint =====

    @Test
    void user_calling_merchants_auth_login_returns_AUTH_007() throws Exception {
        // USER 계정이 상인 로그인 endpoint를 호출하면 LoginService.login의 requiredRole=MERCHANT 검증 실패
        signupUser("user4@example.com", "유저닉4");

        LoginRequest req = new LoginRequest("user4@example.com", PASSWORD);
        mockMvc.perform(post("/api/v1/merchants/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_007"));
    }

    @Test
    void merchant_login_then_merchants_me_ping_returns_200() throws Exception {
        seedFactory.seedMerchant("merchant@example.com", PASSWORD, "상인닉");
        String accessToken = login("/api/v1/merchants/auth/login", "merchant@example.com").accessToken();

        mockMvc.perform(get("/api/v1/merchants/test-auth-fixture")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void merchant_token_calling_admin_me_ping_returns_AUTH_007() throws Exception {
        seedFactory.seedMerchant("merchant2@example.com", PASSWORD, "상인닉2");
        String accessToken = login("/api/v1/merchants/auth/login", "merchant2@example.com").accessToken();

        mockMvc.perform(get("/api/v1/admin/test-auth-fixture")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_007"));
    }

    @Test
    void merchant_token_calling_users_me_ping_returns_AUTH_007() throws Exception {
        // 반대 방향 검증: MERCHANT 토큰으로 USER endpoint 접근도 차단되어야 한다
        seedFactory.seedMerchant("merchant3@example.com", PASSWORD, "상인닉3");
        String accessToken = login("/api/v1/merchants/auth/login", "merchant3@example.com").accessToken();

        mockMvc.perform(get("/api/v1/users/test-auth-fixture")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_007"));
    }

    // ===== ADMIN endpoint =====

    @Test
    void user_calling_admin_auth_login_returns_AUTH_007() throws Exception {
        signupUser("user5@example.com", "유저닉5");

        LoginRequest req = new LoginRequest("user5@example.com", PASSWORD);
        mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_007"));
    }

    @Test
    void admin_login_then_admin_me_ping_returns_200_and_sets_refresh_cookie() throws Exception {
        seedFactory.seedAdmin("admin@example.com", PASSWORD, "관리자닉");
        LoginResult login = login("/api/v1/admin/auth/login", "admin@example.com");

        // Refresh Cookie도 ADMIN 로그인에서 정상 발급되어야 함
        assertThat(login.refreshCookie()).isNotNull();
        assertThat(login.refreshCookie().getValue()).isNotBlank();

        mockMvc.perform(get("/api/v1/admin/test-auth-fixture")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void admin_token_calling_users_test_auth_fixture_returns_AUTH_007() throws Exception {
        // ADMIN 토큰으로 USER endpoint 접근도 차단되어야 한다 (role mismatch 양방향 검증)
        seedFactory.seedAdmin("admin3@example.com", PASSWORD, "관리자닉3");
        String accessToken = login("/api/v1/admin/auth/login", "admin3@example.com").accessToken();

        mockMvc.perform(get("/api/v1/users/test-auth-fixture")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_007"));
    }

    @Test
    void admin_token_calling_merchants_test_auth_fixture_returns_AUTH_007() throws Exception {
        // ADMIN 토큰으로 MERCHANT endpoint 접근도 차단되어야 한다 (role mismatch 양방향 검증)
        seedFactory.seedAdmin("admin4@example.com", PASSWORD, "관리자닉4");
        String accessToken = login("/api/v1/admin/auth/login", "admin4@example.com").accessToken();

        mockMvc.perform(get("/api/v1/merchants/test-auth-fixture")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_007"));
    }

    // ===== 공통 reissue/logout (페이지 무관) =====

    @Test
    void merchant_can_reissue_via_common_auth_endpoint() throws Exception {
        // PRD: reissue/logout은 페이지 분리 없이 /api/v1/auth/** 공통. 토큰을 어디서 받았든 공통 endpoint 사용
        seedFactory.seedMerchant("merchant4@example.com", PASSWORD, "상인닉4");
        LoginResult login = login("/api/v1/merchants/auth/login", "merchant4@example.com");

        mockMvc.perform(post("/api/v1/auth/reissue").cookie(login.refreshCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("MERCHANT"));
    }

    @Test
    void admin_can_logout_via_common_auth_endpoint() throws Exception {
        seedFactory.seedAdmin("admin2@example.com", PASSWORD, "관리자닉2");
        LoginResult login = login("/api/v1/admin/auth/login", "admin2@example.com");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken())
                        .cookie(login.refreshCookie()))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("Max-Age=0")));

        // logout 후 같은 Access로 admin endpoint 호출 → AUTH_013 (블랙리스트)
        mockMvc.perform(get("/api/v1/admin/test-auth-fixture")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_013"));
    }

    // ===== 인증 없이 fixture 호출 시 401 회귀 가드 (HM #2) =====
    // 향후 누군가 SecurityConfig에 fixture endpoint를 permitAll로 추가하는 실수를 차단.

    @Test
    @org.junit.jupiter.api.DisplayName("인증 없이 /api/v1/users/test-auth-fixture 호출 시 401 AUTH_006")
    void anonymous_callingUsersFixture_returns_401_AUTH_006() throws Exception {
        mockMvc.perform(get("/api/v1/users/test-auth-fixture"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_006"));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("인증 없이 /api/v1/merchants/test-auth-fixture 호출 시 401 AUTH_006")
    void anonymous_callingMerchantsFixture_returns_401_AUTH_006() throws Exception {
        mockMvc.perform(get("/api/v1/merchants/test-auth-fixture"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_006"));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("인증 없이 /api/v1/admin/test-auth-fixture 호출 시 401 AUTH_006")
    void anonymous_callingAdminFixture_returns_401_AUTH_006() throws Exception {
        mockMvc.perform(get("/api/v1/admin/test-auth-fixture"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_006"));
    }

    // ===== /api/v1/notifications/** Security 회귀 가드 =====
    // notifications는 USER·MERCHANT 공용 — MERCHANT도 고객센터 알림 수신 대상 (KAN-200). ADMIN·비로그인 차단 검증

    @Test
    @org.junit.jupiter.api.DisplayName("인증 없이 GET /api/v1/notifications 호출 시 401 AUTH_006")
    void anonymous_callingNotifications_returns_401() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_006"));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("MERCHANT 토큰으로 GET /api/v1/notifications 호출 시 200 — 고객센터 알림 수신 대상")
    void merchantToken_callingNotifications_returns_200() throws Exception {
        seedFactory.seedMerchant("merchant-noti@example.com", PASSWORD, "상인닉-알림");
        String accessToken = login("/api/v1/merchants/auth/login", "merchant-noti@example.com").accessToken();

        mockMvc.perform(get("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("인증 없이 PATCH /api/v1/notifications/read-all 호출 시 401 AUTH_006")
    void anonymous_callingNotificationsReadAll_returns_401() throws Exception {
        mockMvc.perform(patch("/api/v1/notifications/read-all"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_006"));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("MERCHANT 토큰으로 PATCH /api/v1/notifications/{id}/read 호출 시 404 — 보안 통과 후 알림 미존재")
    void merchantToken_callingNotificationsRead_returns_404() throws Exception {
        seedFactory.seedMerchant("merchant-noti2@example.com", PASSWORD, "상인닉-알림2");
        String accessToken = login("/api/v1/merchants/auth/login", "merchant-noti2@example.com").accessToken();

        mockMvc.perform(patch("/api/v1/notifications/999999/read")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_001"));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("인증 없이 DELETE /api/v1/notifications/{id} 호출 시 401 AUTH_006")
    void anonymous_callingNotificationsDelete_returns_401() throws Exception {
        mockMvc.perform(delete("/api/v1/notifications/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_006"));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("MERCHANT 토큰으로 DELETE /api/v1/notifications/{id} 호출 시 404 — 보안 통과 후 알림 미존재")
    void merchantToken_callingNotificationsDelete_returns_404() throws Exception {
        seedFactory.seedMerchant("merchant-noti-del@example.com", PASSWORD, "상인닉-알림삭제");
        String accessToken = login("/api/v1/merchants/auth/login", "merchant-noti-del@example.com").accessToken();

        mockMvc.perform(delete("/api/v1/notifications/999999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_001"));
    }

    // ===== /api/v1/recommend/** Security 회귀 가드 =====
    // recommend는 비인증 공개 API — 향후 SecurityConfig에서 실수로 인증 요구를 추가하는 회귀 방지 (KAN-134 누락 사례 재발 방어)

    @Test
    @org.junit.jupiter.api.DisplayName("비인증으로 GET /api/v1/recommend/popular 호출 시 200 — permitAll 회귀 가드")
    void anonymous_callingRecommendPopular_returns_200() throws Exception {
        mockMvc.perform(get("/api/v1/recommend/popular"))
                .andExpect(status().isOk());
    }

    // ===== /api/v1/companion-reviews, /api/v1/users/{id}/companion-score Security 회귀 가드 =====
    // companion-reviews: USER 전용 — 비인증·MERCHANT·ADMIN 차단 (KAN-214, 고도화 KAN-241)
    // companion-score: permitAll — 비인증·USER 모두 허용

    @Test
    @org.junit.jupiter.api.DisplayName("비인증으로 POST /api/v1/companion-reviews 호출 시 401 AUTH_006")
    void anonymous_callingCompanionReviews_returns_401() throws Exception {
        // companion-reviews는 USER 전용 — 비인증 접근 차단 검증
        mockMvc.perform(post("/api/v1/companion-reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_006"));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("MERCHANT 토큰으로 POST /api/v1/companion-reviews 호출 시 403 AUTH_007")
    void merchantToken_callingCompanionReviews_returns_403() throws Exception {
        // companion-reviews는 USER 전용 — MERCHANT 차단 검증
        seedFactory.seedMerchant("merchant-cr@example.com", PASSWORD, "상인닉-리뷰");
        String accessToken = login("/api/v1/merchants/auth/login", "merchant-cr@example.com").accessToken();

        mockMvc.perform(post("/api/v1/companion-reviews")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_007"));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("ADMIN 토큰으로 POST /api/v1/companion-reviews 호출 시 403 AUTH_007")
    void adminToken_callingCompanionReviews_returns_403() throws Exception {
        // companion-reviews는 USER 전용 — ADMIN 차단 검증
        seedFactory.seedAdmin("admin-cr@example.com", PASSWORD, "관리자닉-리뷰");
        String accessToken = login("/api/v1/admin/auth/login", "admin-cr@example.com").accessToken();

        mockMvc.perform(post("/api/v1/companion-reviews")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_007"));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("비인증으로 GET /api/v1/users/{id}/companion-score 호출 시 404 — 보안 통과 후 사용자 미존재")
    void anonymous_callingCompanionScore_returns_404() throws Exception {
        // companion-score는 permitAll — 비인증 접근 시 보안 통과 후 사용자 미존재로 404 반환
        mockMvc.perform(get("/api/v1/users/999999/companion-score"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUTH_015"));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("USER 토큰으로 GET /api/v1/users/{id}/companion-score 호출 시 404 — 보안 통과 후 사용자 미존재")
    void userToken_callingCompanionScore_returns_404() throws Exception {
        // companion-score는 permitAll — USER 토큰으로 접근 시 보안 통과 후 사용자 미존재로 404 반환
        signupUser("user-score@example.com", "유저닉-점수");
        String accessToken = login("/api/v1/users/auth/login", "user-score@example.com").accessToken();

        mockMvc.perform(get("/api/v1/users/999999/companion-score")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUTH_015"));
    }

    // ===== 헬퍼 =====

    /**
     * 도메인 prefix 키를 SCAN으로 순회하며 삭제한다.
     *
     * <p>{@code KEYS} 명령은 O(N) blocking이라 Redis 단일 스레드 모델에서 다른 명령을 모두 지연시킨다.
     * 테스트라도 keyspace가 누적되면 빌드 속도가 떨어지고, CI나 공유 Redis 환경에서는 영향이 크다.
     * SCAN은 cursor 기반 non-blocking이라 안전.
     *
     * <p>{@code try-with-resources}로 cursor를 명시적으로 닫아 connection 누수 방지.
     */
    private void deleteByPrefix(String pattern) {
        org.springframework.data.redis.core.ScanOptions options =
                org.springframework.data.redis.core.ScanOptions.scanOptions()
                        .match(pattern)
                        .count(100)
                        .build();
        java.util.Set<String> keys = new java.util.HashSet<>();
        try (var cursor = redis.scan(options)) {
            cursor.forEachRemaining(keys::add);
        }
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private void signupUser(String email, String nickname) throws Exception {
        SignupRequest request = new SignupRequest(email, PASSWORD, nickname);
        mockMvc.perform(post("/api/v1/users/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private LoginResult login(String loginEndpoint, String email) throws Exception {
        LoginRequest request = new LoginRequest(email, PASSWORD);
        MvcResult result = mockMvc.perform(post(loginEndpoint)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String accessToken = body.get("data").get("accessToken").asString();
        Cookie cookie = result.getResponse().getCookie(COOKIE_NAME);
        return new LoginResult(accessToken, cookie);
    }

    private record LoginResult(String accessToken, Cookie refreshCookie) {
    }
}
