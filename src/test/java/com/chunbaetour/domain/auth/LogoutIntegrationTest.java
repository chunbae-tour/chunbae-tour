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
 * 로그아웃 end-to-end 통합 테스트.
 *
 * <p>PRD AC 핵심 시나리오:
 * <ul>
 *   <li>signup → login → ping (200): 로그인 후 보호 API 호출 정상</li>
 *   <li>logout → 같은 Access Token으로 ping 호출 시 AUTH_013 (블랙리스트 등록 즉시 효과)</li>
 *   <li>logout → 같은 Refresh Cookie로 reissue 시 AUTH_005 (Redis 키 삭제 효과)</li>
 *   <li>logout 응답에 Refresh Cookie 만료(Max-Age=0) 헤더 포함</li>
 *   <li>logout 자체는 Bearer 필요 (토큰 없이 호출 시 AUTH_006)</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
class LogoutIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "logoutuser@example.com";
    private static final String PASSWORD = "Pa$$w0rd1!";
    private static final String NICKNAME = "로그아웃유저";
    private static final String COOKIE_NAME = "refreshToken";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private StringRedisTemplate redis;

    @AfterEach
    void cleanup() {
        accountRepository.deleteAll();
        // S4: refresh + blacklist 두 도메인 prefix 모두 정리. 다른 테스트가 누수된 키로 깨지지 않도록.
        deleteByPrefix("auth:refresh:*");
        deleteByPrefix("auth:blacklist:*");
    }

    @Test
    void signup_login_ping_logout_then_same_token_returns_AUTH_013() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME);
        LoginResult login = login(EMAIL, PASSWORD);

        // 1) 로그아웃 전 ping 200
        mockMvc.perform(get("/api/v1/users/me/ping")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken()))
                .andExpect(status().isOk());

        // 2) 로그아웃 — 204 + 만료 Cookie
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken())
                        .cookie(login.refreshCookie()))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString(COOKIE_NAME + "="),
                                org.hamcrest.Matchers.containsString("Max-Age=0"),
                                org.hamcrest.Matchers.containsString("Path=/api/v1/auth"))));

        // 3) 같은 Access Token으로 ping → AUTH_013 (블랙리스트 효과)
        mockMvc.perform(get("/api/v1/users/me/ping")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_013"));
    }

    @Test
    void logout_then_same_refresh_cookie_reissue_returns_AUTH_005() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME);
        LoginResult login = login(EMAIL, PASSWORD);
        long userId = accountRepository.findByEmail(EMAIL).orElseThrow().getId();
        String refreshKey = "auth:refresh:" + userId;
        assertThat(redis.hasKey(refreshKey)).isTrue();

        // 로그아웃 성공
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken())
                        .cookie(login.refreshCookie()))
                .andExpect(status().isNoContent());

        // 이번 로그인에서 생성된 refresh 키만 삭제됐는지 직접 검증한다.
        assertThat(redis.hasKey(refreshKey)).isFalse();

        // 같은 Refresh Cookie로 reissue → CAS rotate 실패 → AUTH_005
        mockMvc.perform(post("/api/v1/auth/reissue").cookie(login.refreshCookie()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_005"));
    }

    @Test
    void logout_without_token_returns_AUTH_006() throws Exception {
        // S4: /api/v1/auth/logout은 인증 필요 → Bearer 없으면 EntryPoint가 AUTH_006 응답
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_006"));
    }

    @Test
    void logout_with_invalid_token_returns_AUTH_003() throws Exception {
        // 변조된 Bearer로 logout 시도 → 인증 필터가 AUTH_003 (블랙리스트 등록 단계까지 못 감)
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-valid-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_003"));
    }

    private void deleteByPrefix(String pattern) {
        var keys = redis.keys(pattern);
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private void signup(String email, String password, String nickname) throws Exception {
        SignupRequest request = new SignupRequest(email, password, nickname);
        mockMvc.perform(post("/api/v1/users/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private LoginResult login(String email, String password) throws Exception {
        LoginRequest request = new LoginRequest(email, password);
        MvcResult result = mockMvc.perform(post("/api/v1/users/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String accessToken = body.get("data").get("accessToken").asString();
        Cookie cookie = result.getResponse().getCookie(COOKIE_NAME);
        assertThat(cookie).as("login은 refresh cookie를 발급해야 한다").isNotNull();
        return new LoginResult(accessToken, cookie);
    }

    private record LoginResult(String accessToken, Cookie refreshCookie) {
    }
}
