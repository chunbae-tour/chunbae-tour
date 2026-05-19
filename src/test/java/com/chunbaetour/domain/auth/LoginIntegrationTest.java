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
 * 로그인 endpoint 통합 테스트.
 *
 * <p>S3 마이그레이션 핵심 변경:
 * <ul>
 *   <li>Body {@code refreshToken} assertion 제거 (필드 자체가 사라짐)</li>
 *   <li>Set-Cookie 헤더 검증 추가 (HttpOnly, SameSite, Path 포함)</li>
 *   <li>{@link AbstractIntegrationTest} 상속으로 Redis 컨테이너 추가</li>
 *   <li>Redis에 refresh tokenId가 실제로 저장되는지 함께 검증</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
class LoginIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "loginuser@example.com";
    private static final String PASSWORD = "Pa$$w0rd1!";
    private static final String NICKNAME = "로그인유저";
    private static final String COOKIE_NAME = "refreshToken";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private StringRedisTemplate redis;

    /**
     * MySQL 데이터 + Redis refresh 키를 동시에 정리한다.
     * Redis는 JVM 단위 공유라 누수 시 다른 테스트가 깨지므로 prefix 기반 cleanup 필수.
     */
    @AfterEach
    void cleanup() {
        accountRepository.deleteAll();
        var keys = redis.keys("auth:refresh:*");
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    @Test
    void signup_then_login_then_ping_returns_200_with_userId() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME);
        LoginResult login = login(EMAIL, PASSWORD);

        mockMvc.perform(get("/api/v1/users/me/ping")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.userId").isNumber());
    }

    @Test
    void login_sets_httpOnly_refresh_cookie_with_security_attributes() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME);

        MvcResult result = mockMvc.perform(post("/api/v1/users/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(EMAIL, PASSWORD))))
                .andExpect(status().isOk())
                // Body에는 refreshToken이 없어야 함 (S3 변경 사항)
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.role").value("USER"))
                // Set-Cookie 헤더로 refreshToken 전달 + 보안 속성 모두 명시
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString(COOKIE_NAME + "="),
                                org.hamcrest.Matchers.containsString("HttpOnly"),
                                org.hamcrest.Matchers.containsString("SameSite=Lax"),
                                org.hamcrest.Matchers.containsString("Path=/api/v1/auth"),
                                // 테스트 환경은 secure=false. 다른 환경에서 Secure가 잘못 들어가면 회귀
                                org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Secure")))))
                .andReturn();

        // 실제 Cookie 객체로도 추출 가능해야 함 (MockMvc 응답에서 파싱)
        Cookie cookie = result.getResponse().getCookie(COOKIE_NAME);
        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isNotBlank();
        assertThat(cookie.isHttpOnly()).isTrue();
    }

    @Test
    void login_stores_refresh_tokenId_in_redis() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME);

        login(EMAIL, PASSWORD);

        // Redis 키가 정확히 1개 생성되었는지 (한 사용자 = 한 세션 모델 검증)
        var keys = redis.keys("auth:refresh:*");
        assertThat(keys).hasSize(1);
    }

    @Test
    void login_with_wrong_password_returns_401_AUTH_001() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME);

        LoginRequest wrong = new LoginRequest(EMAIL, "Wrong!Pass1");
        mockMvc.perform(post("/api/v1/users/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrong)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_001"));

        // 실패 시 Redis에 키가 생기면 안 됨 (LoginService 분기 검증)
        var keys = redis.keys("auth:refresh:*");
        assertThat(keys).isEmpty();
    }

    @Test
    void ping_without_token_returns_401_AUTH_006() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_006"));
    }

    @Test
    void ping_with_invalid_token_returns_401_AUTH_003() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/ping")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-valid-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_003"));
    }

    /** 회원가입 헬퍼. 후속 시나리오의 사전 조건 구성용. */
    private void signup(String email, String password, String nickname) throws Exception {
        SignupRequest request = new SignupRequest(email, password, nickname);
        mockMvc.perform(post("/api/v1/users/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    /**
     * 로그인 헬퍼. Body의 accessToken + Set-Cookie의 refreshToken을 함께 반환해
     * reissue/ping 후속 호출에서 둘 다 사용할 수 있다.
     */
    private LoginResult login(String email, String password) throws Exception {
        LoginRequest request = new LoginRequest(email, password);
        MvcResult result = mockMvc.perform(post("/api/v1/users/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String accessToken = body.get("data").get("accessToken").asString();
        Cookie cookie = result.getResponse().getCookie(COOKIE_NAME);
        return new LoginResult(accessToken, cookie);
    }

    /** 로그인 결과 묶음 (accessToken + refresh Cookie). */
    private record LoginResult(String accessToken, Cookie refreshCookie) {
    }
}
