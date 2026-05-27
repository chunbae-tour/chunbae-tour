package com.chunbaetour.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 회원 탈퇴 end-to-end 통합 테스트 (Epic C S1, KAN-143).
 *
 * <p>PRD AC 핵심 시나리오:
 * <ul>
 *   <li>signup → login → DELETE /me → 204 + Set-Cookie Max-Age=0 + Account.deletedAt 세팅 + status=DELETED</li>
 *   <li>탈퇴 후 같은 access token으로 GET /me → AUTH_013 (blacklist 효과)</li>
 *   <li>탈퇴 후 같은 refresh cookie로 POST /auth/reissue → AUTH_005 (Redis 삭제 효과)</li>
 *   <li>미인증 DELETE /me → AUTH_006</li>
 * </ul>
 *
 * <p><b>중복 탈퇴 호출 테스트는 본 클래스에 포함하지 않는다</b>: 첫 탈퇴 직후 access blacklist 등록이
 * 끝나므로 같은 토큰으로 두 번째 호출 시 JwtAuthenticationFilter가 AUTH_013으로 차단해 도메인 분기에
 * 도달 불가. {@code Account.softDelete} {@link IllegalStateException} 가드는 {@code AccountTest}가 커버.
 *
 * <p><b>cleanup 주의</b>: {@code @SQLRestriction("deleted_at IS NULL")} 때문에 JpaRepository.deleteAll은
 * 탈퇴(soft delete)된 사용자 row를 SELECT에서 못 찾아 지우지 못한다. 본 테스트가 명시적으로 DELETE 흐름을
 * 검증하므로 cleanup은 native SQL({@code DELETE FROM users})로 진행해 다음 테스트 격리.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccountWithdrawalIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "withdraw@example.com";
    private static final String PASSWORD = "Pa$$w0rd1!";
    private static final String NICKNAME = "탈퇴테스터";
    private static final String REFRESH_COOKIE_NAME = "refreshToken";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanup() {
        // wallets는 FK는 없지만 userId 기준 row가 남으면 후속 테스트에서 잔여 잔액 조회가 어긋날 수 있어 함께 정리.
        jdbcTemplate.execute("DELETE FROM wallets");
        // @SQLRestriction이 JPA delete에는 SELECT 필터로 작용해 soft-deleted row가 살아남음 → native delete.
        jdbcTemplate.execute("DELETE FROM users");
        var refreshKeys = redis.keys("auth:refresh:*");
        if (!refreshKeys.isEmpty()) {
            redis.delete(refreshKeys);
        }
        var blacklistKeys = redis.keys("auth:blacklist:*");
        if (!blacklistKeys.isEmpty()) {
            redis.delete(blacklistKeys);
        }
    }

    // ===== KAN-143 Epic C S1 — DELETE /users/me 정상 흐름 =====

    @Test
    void delete_me_returns_204_and_expires_refresh_cookie() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME);
        LoginResult login = login(EMAIL, PASSWORD);

        mockMvc.perform(delete("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken()))
                .andExpect(status().isNoContent())
                // Set-Cookie 헤더로 Refresh Cookie 즉시 만료 — 발급 시와 같은 name/path여야 브라우저가 덮어쓴다
                .andExpect(cookie().exists(REFRESH_COOKIE_NAME))
                .andExpect(cookie().maxAge(REFRESH_COOKIE_NAME, 0))
                .andExpect(cookie().value(REFRESH_COOKIE_NAME, ""))
                .andExpect(cookie().path(REFRESH_COOKIE_NAME, "/api/v1/auth"));

        // DB 상태 회귀 가드: deletedAt 세팅 + status=DELETED.
        // @SQLRestriction으로 JPA findById는 빈 결과 → native SQL로 직접 조회해 검증.
        var row = jdbcTemplate.queryForMap(
                "SELECT status, deleted_at FROM users WHERE email = ?", EMAIL);
        assertThat(row.get("status")).isEqualTo("DELETED");
        assertThat(row.get("deleted_at")).isNotNull();
    }

    @Test
    void after_withdrawal_same_access_token_is_rejected_with_AUTH_013() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME);
        LoginResult login = login(EMAIL, PASSWORD);

        // 탈퇴 호출 — afterCommit이 access token blacklist 등록
        mockMvc.perform(delete("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken()))
                .andExpect(status().isNoContent());

        // 같은 access token으로 보호 API 호출 시 JwtAuthenticationFilter가 blacklist 확인 → AUTH_013
        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_013"));
    }

    @Test
    void after_withdrawal_same_refresh_cookie_reissue_is_rejected_with_AUTH_005() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME);
        LoginResult login = login(EMAIL, PASSWORD);

        // 탈퇴 호출 — afterCommit이 auth:refresh:{userId} Redis 키 삭제
        mockMvc.perform(delete("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken()))
                .andExpect(status().isNoContent());

        // 같은 refresh cookie로 reissue 시도. JWT 서명/만료는 살아있지만 Redis 키가 사라져 ReissueService의
        // rotate CAS가 실패 → AUTH_005.
        mockMvc.perform(post("/api/v1/auth/reissue")
                        .cookie(login.refreshCookie()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_005"));
    }

    @Test
    void delete_me_without_token_returns_401_AUTH_006() throws Exception {
        // SecurityConfig가 /api/v1/users/** = hasRole(USER)로 매핑 — 인증 없으면 EntryPoint가 AUTH_006.
        mockMvc.perform(delete("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_006"));
    }

    // ===== helpers =====

    private void signup(String email, String password, String nickname) throws Exception {
        SignupRequest request = new SignupRequest(email, password, nickname);
        mockMvc.perform(post("/api/v1/users/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    /**
     * 로그인 후 access token + refresh cookie를 함께 회수한다.
     *
     * <p>UserMeIntegrationTest의 login()은 access만 반환하지만 본 테스트는 reissue 차단 시나리오에서
     * 원본 refresh cookie가 필요해 별도 helper를 둔다.
     */
    private LoginResult login(String email, String password) throws Exception {
        LoginRequest request = new LoginRequest(email, password);
        MvcResult result = mockMvc.perform(post("/api/v1/users/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String accessToken = body.get("data").get("accessToken").asString();
        Cookie refreshCookie = result.getResponse().getCookie(REFRESH_COOKIE_NAME);
        assertThat(refreshCookie)
                .as("로그인 응답에 refresh cookie가 포함돼야 후속 reissue 차단 시나리오 검증 가능")
                .isNotNull();
        return new LoginResult(accessToken, refreshCookie);
    }

    private record LoginResult(String accessToken, Cookie refreshCookie) {
    }
}
