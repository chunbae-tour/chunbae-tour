package com.chunbaetour.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
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
import tools.jackson.databind.ObjectMapper;

/**
 * Refresh Token Rotation end-to-end 통합 테스트.
 *
 * <p>PRD AC 커버리지:
 * <ul>
 *   <li>signup → login → reissue (새 Access + 새 Refresh Cookie 반환)</li>
 *   <li>이전 Refresh Cookie로 재요청 시 AUTH_005 (Rotation 후 stale 토큰 거부)</li>
 *   <li>Cookie 없이 reissue 호출 시 AUTH_005</li>
 *   <li>HttpOnly/SameSite/Path 등 보안 속성 검증</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReissueIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "reissueuser@example.com";
    private static final String PASSWORD = "Pa$$w0rd1!";
    private static final String NICKNAME = "재발급유저";
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
        var keys = redis.keys("auth:refresh:*");
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    @Test
    void login_then_reissue_returns_new_access_and_new_refresh_cookie() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME);
        Cookie firstRefresh = login(EMAIL, PASSWORD);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/reissue").cookie(firstRefresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.accessToken").isString())
                // refreshToken은 Body에 절대 노출되면 안 됨
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.data.role").value("USER"))
                // Set-Cookie로 새 refreshToken 전달
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString(COOKIE_NAME + "="),
                                org.hamcrest.Matchers.containsString("HttpOnly"),
                                org.hamcrest.Matchers.containsString("SameSite=Lax"),
                                org.hamcrest.Matchers.containsString("Path=/api/v1/auth"))))
                .andReturn();

        Cookie newRefresh = result.getResponse().getCookie(COOKIE_NAME);
        assertThat(newRefresh).isNotNull();
        // Rotation: 새 토큰 값은 이전 토큰과 달라야 함
        assertThat(newRefresh.getValue()).isNotEqualTo(firstRefresh.getValue());
    }

    @Test
    void reissue_then_old_refresh_is_invalidated_AUTH_005() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME);
        Cookie firstRefresh = login(EMAIL, PASSWORD);

        // 첫 reissue 성공 → Redis에 새 tokenId 저장됨, 이전 tokenId는 사라짐
        mockMvc.perform(post("/api/v1/auth/reissue").cookie(firstRefresh))
                .andExpect(status().isOk());

        // 이전(첫 번째) Refresh Cookie로 다시 reissue 시도 → CAS 실패 → AUTH_005
        mockMvc.perform(post("/api/v1/auth/reissue").cookie(firstRefresh))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_005"));
    }

    @Test
    void cas_failure_invalidates_whole_refresh_series_both_blocked() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME);
        Cookie firstRefresh = login(EMAIL, PASSWORD); // R1, Redis = R1

        // race에서 이긴 쪽(탈취 의심 측)이 먼저 reissue 성공 → R2 획득, Redis = R2
        MvcResult won = mockMvc.perform(post("/api/v1/auth/reissue").cookie(firstRefresh))
                .andExpect(status().isOk())
                .andReturn();
        Cookie wonRefresh = won.getResponse().getCookie(COOKIE_NAME); // R2
        assertThat(wonRefresh).as("이긴 쪽은 새 Refresh(R2)를 받아야 함").isNotNull();

        // 진 쪽(정상 사용자)이 stale R1로 reissue → CAS 실패 → AUTH_005 + 계열 무효화 트리거
        mockMvc.perform(post("/api/v1/auth/reissue").cookie(firstRefresh))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_005"));

        // 계열 무효화 결과: 이긴 쪽이 보유한 R2도 더 이상 reissue 불가 → "둘 다 차단"
        mockMvc.perform(post("/api/v1/auth/reissue").cookie(wonRefresh))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_005"));

        // Redis refresh 키가 실제로 삭제됐는지 확인 (계열 무효화 사실 검증)
        assertThat(redis.keys("auth:refresh:*")).isEmpty();
    }

    @Test
    void reissue_without_cookie_returns_AUTH_005() throws Exception {
        // Cookie 없이 reissue 직접 호출 (정상 흐름이 아닌 비정상 경로)
        mockMvc.perform(post("/api/v1/auth/reissue"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_005"));
    }

    @Test
    void reissue_with_invalid_refresh_token_returns_AUTH_005() throws Exception {
        Cookie tampered = new Cookie(COOKIE_NAME, "not.a.valid.jwt");

        mockMvc.perform(post("/api/v1/auth/reissue").cookie(tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_005"));
    }

    private void signup(String email, String password, String nickname) throws Exception {
        SignupRequest request = new SignupRequest(email, password, nickname);
        mockMvc.perform(post("/api/v1/users/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    /** 로그인 헬퍼: refresh Cookie만 반환 (reissue 흐름에서 Body access는 미사용). */
    private Cookie login(String email, String password) throws Exception {
        LoginRequest request = new LoginRequest(email, password);
        MvcResult result = mockMvc.perform(post("/api/v1/users/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie(COOKIE_NAME);
        assertThat(cookie).as("login은 refresh Cookie를 발급해야 함").isNotNull();
        return cookie;
    }
}
