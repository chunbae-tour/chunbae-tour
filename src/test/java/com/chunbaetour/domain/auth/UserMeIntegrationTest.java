package com.chunbaetour.domain.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.auth.dto.LoginRequest;
import com.chunbaetour.domain.auth.dto.SignupRequest;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
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
 * 마이페이지 본인 정보 조회 end-to-end 통합 테스트 (Epic A S1, KAN-63).
 *
 * <p>PRD AC 핵심 시나리오:
 * <ul>
 *   <li>signup → login → GET /me → 200 + 필드 검증 + 민감 필드(password) 미노출</li>
 *   <li>비인증 호출 시 AUTH_006 (RestAuthenticationEntryPoint 응답)</li>
 *   <li>응답 필드가 sa-docs/04 F-USER-001 사양과 일치 (email/nickname/role/status 등)</li>
 * </ul>
 *
 * <p>MERCHANT/ADMIN 토큰으로 /users/me 접근 시 AUTH_007 검증은 KAN-49 S5의 MultiRoleAuthIntegrationTest에서
 * 이미 커버됨 (URL 권한 매핑 검증). 본 클래스에서는 USER 흐름만 집중.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserMeIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "meuser@example.com";
    private static final String PASSWORD = "Pa$$w0rd1!";
    private static final String NICKNAME = "마이유저";

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
        var refreshKeys = redis.keys("auth:refresh:*");
        if (!refreshKeys.isEmpty()) {
            redis.delete(refreshKeys);
        }
        var blacklistKeys = redis.keys("auth:blacklist:*");
        if (!blacklistKeys.isEmpty()) {
            redis.delete(blacklistKeys);
        }
    }

    @Test
    void signup_login_then_get_me_returns_200_with_expected_fields() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME);
        String accessToken = login(EMAIL, PASSWORD);

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.userId").isNumber())
                .andExpect(jsonPath("$.data.email").value(EMAIL))
                .andExpect(jsonPath("$.data.nickname").value(NICKNAME))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.language").value("ko"))
                .andExpect(jsonPath("$.data.companionScore").value(0.0))
                .andExpect(jsonPath("$.data.companionReviewCount").value(0))
                .andExpect(jsonPath("$.data.createdAt").exists())
                // 보안 회귀 방지: 민감 필드는 응답에 절대 노출되면 안 됨
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.deletedAt").doesNotExist())
                .andExpect(jsonPath("$.data.suspendedUntil").doesNotExist());
    }

    @Test
    void get_me_without_token_returns_401_AUTH_006() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_006"));
    }

    @Test
    void get_me_with_invalid_token_returns_401_AUTH_003() throws Exception {
        // 변조된 Bearer는 JwtAuthenticationFilter에서 verifyAccess 실패 → AUTH_003
        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-valid-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_003"));
    }

    private void signup(String email, String password, String nickname) throws Exception {
        SignupRequest request = new SignupRequest(email, password, nickname);
        mockMvc.perform(post("/api/v1/users/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private String login(String email, String password) throws Exception {
        LoginRequest request = new LoginRequest(email, password);
        MvcResult result = mockMvc.perform(post("/api/v1/users/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("data").get("accessToken").asString();
    }
}
