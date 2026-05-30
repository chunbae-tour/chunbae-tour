package com.chunbaetour.domain.payment.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.auth.dto.LoginRequest;
import com.chunbaetour.domain.auth.dto.SignupRequest;
import com.chunbaetour.domain.payment.repository.PaymentOrderRepository;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import com.chunbaetour.domain.yeopjeon.repository.WalletRepository;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * GET /api/v1/payments/history 보안 및 파라미터 유효성 통합 테스트.
 *
 * 검증 시나리오:
 * - 비인증 접근 → 401
 * - 변조 토큰 접근 → 401
 * - size 범위 초과(@Min(1)/@Max(100)) → 400
 * - 인증 USER → 200 빈 커서 페이지
 *
 * 소유권 시나리오(@AuthenticationPrincipal):
 * 본 엔드포인트는 토큰의 userId로만 조회하므로 타인 데이터에 접근할 경로가 없음.
 * userId를 path param으로 받는 구조가 아니므로 별도 소유권 테스트 불필요.
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
class PaymentHistoryControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "payhistory@example.com";
    private static final String PASSWORD = "Pa$$w0rd1!";
    private static final String NICKNAME = "결제이력유저";
    private static final String ENDPOINT = "/api/v1/payments/history";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AccountRepository accountRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private PaymentOrderRepository paymentOrderRepository;
    @Autowired private StringRedisTemplate redis;

    @AfterEach
    void cleanup() {
        paymentOrderRepository.deleteAll();
        walletRepository.deleteAll();
        accountRepository.deleteAll();
        deleteKeysByScan("auth:refresh:*");
        deleteKeysByScan("auth:blacklist:*");
        deleteKeysByScan("idempotency:*");
    }

    @Test
    @DisplayName("토큰 없이 접근 시 401 AUTH_006(인증 필요)을 반환한다")
    void getHistory_without_token_returns_401() throws Exception {
        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_006"));
    }

    @Test
    @DisplayName("변조된 토큰으로 접근 시 401 AUTH_003(토큰 검증 실패)을 반환한다")
    void getHistory_with_invalid_token_returns_401() throws Exception {
        mockMvc.perform(get(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_003"));
    }

    @Test
    @DisplayName("인증된 USER 토큰으로 접근 시 200과 빈 커서 페이지를 반환한다")
    void getHistory_with_valid_token_returns_200_empty() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME);
        String token = login(EMAIL, PASSWORD);

        mockMvc.perform(get(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    @DisplayName("size=0 요청 시 400 COMMON_002(@Min 유효성 검증)")
    void getHistory_size0_returns_400() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME);
        String token = login(EMAIL, PASSWORD);

        mockMvc.perform(get(ENDPOINT + "?size=0")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));
    }

    @Test
    @DisplayName("size=101 요청 시 400 COMMON_002(@Max 유효성 검증)")
    void getHistory_size101_returns_400() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME);
        String token = login(EMAIL, PASSWORD);

        mockMvc.perform(get(ENDPOINT + "?size=101")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));
    }

    private void deleteKeysByScan(String pattern) {
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
        Set<String> keys = new HashSet<>();
        try (var cursor = redis.scan(options)) {
            cursor.forEachRemaining(keys::add);
        }
        if (!keys.isEmpty()) redis.delete(keys);
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
