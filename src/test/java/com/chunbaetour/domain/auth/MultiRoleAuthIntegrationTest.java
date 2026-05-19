package com.chunbaetour.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.auth.dto.LoginRequest;
import com.chunbaetour.domain.auth.dto.SignupRequest;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import com.chunbaetour.domain.support.AccountSeedFactory;
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
 *   <li>발급된 토큰은 매칭되는 me/ping endpoint만 통과 — 다른 page의 me/ping은 AUTH_007</li>
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

        mockMvc.perform(get("/api/v1/users/me/ping")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void user_token_calling_merchants_me_ping_returns_AUTH_007() throws Exception {
        signupUser("user2@example.com", "유저닉2");
        String accessToken = login("/api/v1/users/auth/login", "user2@example.com").accessToken();

        mockMvc.perform(get("/api/v1/merchants/me/ping")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_007"));
    }

    @Test
    void user_token_calling_admin_me_ping_returns_AUTH_007() throws Exception {
        signupUser("user3@example.com", "유저닉3");
        String accessToken = login("/api/v1/users/auth/login", "user3@example.com").accessToken();

        mockMvc.perform(get("/api/v1/admin/me/ping")
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

        mockMvc.perform(get("/api/v1/merchants/me/ping")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void merchant_token_calling_admin_me_ping_returns_AUTH_007() throws Exception {
        seedFactory.seedMerchant("merchant2@example.com", PASSWORD, "상인닉2");
        String accessToken = login("/api/v1/merchants/auth/login", "merchant2@example.com").accessToken();

        mockMvc.perform(get("/api/v1/admin/me/ping")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_007"));
    }

    @Test
    void merchant_token_calling_users_me_ping_returns_AUTH_007() throws Exception {
        // 반대 방향 검증: MERCHANT 토큰으로 USER endpoint 접근도 차단되어야 한다
        seedFactory.seedMerchant("merchant3@example.com", PASSWORD, "상인닉3");
        String accessToken = login("/api/v1/merchants/auth/login", "merchant3@example.com").accessToken();

        mockMvc.perform(get("/api/v1/users/me/ping")
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

        mockMvc.perform(get("/api/v1/admin/me/ping")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken()))
                .andExpect(status().isOk());
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
        mockMvc.perform(get("/api/v1/admin/me/ping")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_013"));
    }

    // ===== 헬퍼 =====

    private void deleteByPrefix(String pattern) {
        var keys = redis.keys(pattern);
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
