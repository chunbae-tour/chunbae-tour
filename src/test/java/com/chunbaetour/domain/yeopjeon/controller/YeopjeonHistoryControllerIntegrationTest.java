package com.chunbaetour.domain.yeopjeon.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.auth.dto.LoginRequest;
import com.chunbaetour.domain.auth.dto.SignupRequest;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import com.chunbaetour.domain.yeopjeon.repository.WalletRepository;
import com.chunbaetour.domain.yeopjeon.repository.YeopjeonHistoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * GET /api/v1/yeopjeon/histories 보안 및 기본 동작 통합 테스트.
 * <p>
 * 검증 시나리오:
 * - 비인증 접근 → 401 AUTH_006
 * - 변조 토큰 접근 → 401 AUTH_003
 * - 인증 USER 접근 → 200 빈 커서 페이지
 * <p>
 * PAY_011(PAYMENT_HISTORY_FORBIDDEN) 참고:
 * 현재 엔드포인트는 @AuthenticationPrincipal로 본인 이력만 조회하므로
 * 타인 이력 접근 경로가 없어 PAY_011 시나리오는 해당 없음.
 * 타인 userId를 path param으로 받는 엔드포인트 추가 시 별도 테스트 필요.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "portone.secret=test-secret",
        "portone.webhook-secret=test-webhook-secret",
        "portone.store-id=test-store",
        "portone.base-url=http://localhost:9999",
        "portone.channel.card=test-channel-card"
})
class YeopjeonHistoryControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "history@example.com";
    private static final String PASSWORD = "Pa$$w0rd1!";
    private static final String NICKNAME = "이력유저";
    private static final String ENDPOINT = "/api/v1/yeopjeon/histories";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AccountRepository accountRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private YeopjeonHistoryRepository yeopjeonHistoryRepository;
    @Autowired private StringRedisTemplate redis;

    @AfterEach
    void cleanup() {
        // 테스트 간 데이터 누수 방지: 이력 → 지갑 → 계정 순으로 삭제 (FK 의존 순서)
        yeopjeonHistoryRepository.deleteAll();
        walletRepository.deleteAll();
        accountRepository.deleteAll();
        var refreshKeys = redis.keys("auth:refresh:*");
        if (refreshKeys != null && !refreshKeys.isEmpty()) redis.delete(refreshKeys);
        var blacklistKeys = redis.keys("auth:blacklist:*");
        if (blacklistKeys != null && !blacklistKeys.isEmpty()) redis.delete(blacklistKeys);
    }

    @Test
    @DisplayName("토큰 없이 접근 시 401 AUTH_006(인증 필요)을 반환한다")
    void getHistories_without_token_returns_401_AUTH_006() throws Exception {
        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_006"));
    }

    @Test
    @DisplayName("변조된 토큰으로 접근 시 401 AUTH_003(토큰 검증 실패)을 반환한다")
    void getHistories_with_invalid_token_returns_401_AUTH_003() throws Exception {
        mockMvc.perform(get(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_003"));
    }

    @Test
    @DisplayName("인증된 USER 토큰으로 접근 시 200과 빈 이력 커서 페이지를 반환한다")
    void getHistories_with_valid_token_returns_200_empty_cursor_page() throws Exception {
        // 회원가입 → 로그인 → 이력 조회
        signup(EMAIL, PASSWORD, NICKNAME);
        String accessToken = login(EMAIL, PASSWORD);

        mockMvc.perform(get(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist());
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
