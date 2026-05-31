package com.chunbaetour.domain.payment.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.auth.dto.LoginRequest;
import com.chunbaetour.domain.auth.dto.SignupRequest;
import com.chunbaetour.domain.payment.entity.PaymentOrder;
import com.chunbaetour.domain.payment.repository.PaymentOrderRepository;
import com.chunbaetour.domain.payment.type.PaymentMethod;
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

    @Test
    @DisplayName("두 사용자 주문 seed 시 호출자 본인 row만 반환한다 (userId 스코프 회귀 가드)")
    void getHistory_returns_only_callers_orders() throws Exception {
        // 유저1 가입·로그인
        signup(EMAIL, PASSWORD, NICKNAME);
        String token1 = login(EMAIL, PASSWORD);
        Long userId1 = extractUserId(token1);

        // 유저2 가입
        signup("other@example.com", PASSWORD, "타인유저");
        String token2 = login("other@example.com", PASSWORD);
        Long userId2 = extractUserId(token2);

        // 유저1 주문 1건, 유저2 주문 1건 직접 seed
        paymentOrderRepository.save(PaymentOrder.create("uid-user1", userId1, 10_000L, "idem-u1", PaymentMethod.CARD, "uid-user1"));
        paymentOrderRepository.save(PaymentOrder.create("uid-user2", userId2, 20_000L, "idem-u2", PaymentMethod.CARD, "uid-user2"));

        // 유저1로 조회 — 유저1 주문 1건만 반환, createdAt 포함 필드 검증
        mockMvc.perform(get(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].orderUid").value("uid-user1"))
                .andExpect(jsonPath("$.data.content[0].amount").value(10_000))
                .andExpect(jsonPath("$.data.content[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    @DisplayName("cursor round-trip: 1페이지 nextCursor로 2페이지 조회 시 중복 없이 hasNext=false 반환한다")
    void getHistory_cursor_roundtrip() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME);
        String token = login(EMAIL, PASSWORD);
        Long userId = extractUserId(token);

        // 주문 3건 seed — id DESC 정렬이므로 마지막 저장 순서가 먼저 반환됨
        paymentOrderRepository.save(PaymentOrder.create("uid-c1", userId, 5_000L, "idem-c1", PaymentMethod.CARD, "uid-c1"));
        paymentOrderRepository.save(PaymentOrder.create("uid-c2", userId, 10_000L, "idem-c2", PaymentMethod.CARD, "uid-c2"));
        paymentOrderRepository.save(PaymentOrder.create("uid-c3", userId, 15_000L, "idem-c3", PaymentMethod.CARD, "uid-c3"));

        // 1페이지: size=2
        MvcResult page1 = mockMvc.perform(get(ENDPOINT + "?size=2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andReturn();

        JsonNode page1Body = objectMapper.readTree(page1.getResponse().getContentAsString());
        String nextCursor = page1Body.get("data").get("nextCursor").asText();
        String page1Uid0 = page1Body.get("data").get("content").get(0).get("orderUid").asText();
        String page1Uid1 = page1Body.get("data").get("content").get(1).get("orderUid").asText();

        // 2페이지: nextCursor 사용
        MvcResult page2 = mockMvc.perform(get(ENDPOINT + "?size=2&cursor=" + nextCursor)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andReturn();

        JsonNode page2Body = objectMapper.readTree(page2.getResponse().getContentAsString());
        String page2Uid0 = page2Body.get("data").get("content").get(0).get("orderUid").asText();

        // 1페이지와 2페이지 간 중복 없음
        org.assertj.core.api.Assertions.assertThat(page2Uid0)
                .isNotEqualTo(page1Uid0)
                .isNotEqualTo(page1Uid1);
    }

    @Test
    @DisplayName("잘못된 cursor 값 요청 시 400 COMMON_008(유효하지 않은 커서)을 반환한다")
    void getHistory_invalid_cursor_returns_400() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME);
        String token = login(EMAIL, PASSWORD);

        mockMvc.perform(get(ENDPOINT + "?cursor=invalid-cursor-value!!!")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_008"));
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
        return body.get("data").get("accessToken").asText();
    }

    private Long extractUserId(String token) {
        // userId는 JWT subject(sub)에 저장됨 — TokenIssuer.issueAccess 참고
        String[] parts = token.split("\\.");
        String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
        try {
            JsonNode node = objectMapper.readTree(payload);
            return node.get("sub").asLong();
        } catch (Exception e) {
            throw new RuntimeException("JWT payload sub 파싱 실패", e);
        }
    }
}
