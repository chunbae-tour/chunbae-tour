package com.chunbaetour.domain.shop.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.auth.Role;
import com.chunbaetour.domain.auth.dto.LoginRequest;
import com.chunbaetour.domain.auth.dto.SignupRequest;
import com.chunbaetour.domain.auth.jwt.TokenIssuer;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.entity.ShopWallet;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.shop.repository.ShopWalletRepository;
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
 * PUT /api/v1/merchants/me/shops/{shopId}/account 보안 및 비즈니스 통합 테스트.
 *
 * 검증 시나리오:
 * - 비인증 접근 → 401 AUTH_006
 * - 변조 토큰 접근 → 401 AUTH_003
 * - USER 권한으로 MERCHANT 경로 접근 → 403 AUTH_007
 * - MERCHANT 토큰 + 본인 가게 → 200 성공 + 계좌번호 마스킹 검증
 * - MERCHANT 토큰 + 타인 가게 shopId → 404 SHOP_001
 * - accountNumber 형식 오류 → 400 COMMON_002
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
class ShopAccountControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "shopaccount@example.com";
    private static final String PASSWORD = "Pa$$w0rd1!";
    private static final String NICKNAME = "계좌테스트유저";
    private static final String BASE_ENDPOINT = "/api/v1/merchants/me/shops";

    private static final String VALID_BODY = """
            {"bankName":"국민은행","accountNumber":"123456-78-901234","accountHolder":"홍길동"}
            """;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AccountRepository accountRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private ShopRepository shopRepository;
    @Autowired private ShopWalletRepository shopWalletRepository;
    @Autowired private TokenIssuer tokenIssuer;
    @Autowired private StringRedisTemplate redis;

    @AfterEach
    void cleanup() {
        shopWalletRepository.deleteAll();
        shopRepository.deleteAll();
        walletRepository.deleteAll();
        accountRepository.deleteAll();
        deleteKeysByScan("auth:refresh:*");
        deleteKeysByScan("auth:blacklist:*");
    }

    @Test
    @DisplayName("토큰 없이 접근 시 401 AUTH_006(인증 필요)을 반환한다")
    void updateAccount_without_token_returns_401() throws Exception {
        mockMvc.perform(put(BASE_ENDPOINT + "/1/account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_006"));
    }

    @Test
    @DisplayName("변조된 토큰으로 접근 시 401 AUTH_003(토큰 검증 실패)을 반환한다")
    void updateAccount_with_invalid_token_returns_401() throws Exception {
        mockMvc.perform(put(BASE_ENDPOINT + "/1/account")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_003"));
    }

    @Test
    @DisplayName("USER 권한 토큰으로 MERCHANT 경로 접근 시 403 AUTH_007을 반환한다")
    void updateAccount_with_user_token_returns_403() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME);
        String userToken = login(EMAIL, PASSWORD);

        mockMvc.perform(put(BASE_ENDPOINT + "/1/account")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_007"));
    }

    @Test
    @DisplayName("MERCHANT 토큰 + 본인 가게 PUT 성공 → 200, 계좌번호 마스킹 검증")
    void updateAccount_merchant_success() throws Exception {
        Long merchantUserId = 9001L;
        String token = tokenIssuer.issueAccess(merchantUserId, Role.MERCHANT, "merchant@test.com");

        Shop shop = shopRepository.save(Shop.builder()
                .userId(merchantUserId).applicationId(9001L)
                .shopName("테스트가게").category("FOOD")
                .address("서울시 강남구").phone("02-0000-0000")
                .description("테스트").build());
        shopWalletRepository.save(ShopWallet.create(shop.getId()));

        mockMvc.perform(put(BASE_ENDPOINT + "/" + shop.getId() + "/account")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.bankName").value("국민은행"))
                .andExpect(jsonPath("$.data.accountHolder").value("홍길동"))
                // 계좌번호 마스킹: 123456-78-901234 → ******-**-**1234 (하이픈 유지, 숫자만 *)
                .andExpect(jsonPath("$.data.accountNumber").value("******-**-**1234"));
    }

    @Test
    @DisplayName("MERCHANT 토큰 + 타인 shopId 접근 시 404 SHOP_001을 반환한다")
    void updateAccount_other_shop_returns_404() throws Exception {
        Long merchantUserId = 9002L;
        String token = tokenIssuer.issueAccess(merchantUserId, Role.MERCHANT, "merchant2@test.com");

        mockMvc.perform(put(BASE_ENDPOINT + "/99999/account")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHOP_001"));
    }

    @Test
    @DisplayName("accountNumber 형식 오류(하이픈만) 시 400 COMMON_002를 반환한다")
    void updateAccount_invalid_accountNumber_returns_400() throws Exception {
        Long merchantUserId = 9003L;
        String token = tokenIssuer.issueAccess(merchantUserId, Role.MERCHANT, "merchant3@test.com");
        String invalidBody = """
                {"bankName":"국민은행","accountNumber":"----------","accountHolder":"홍길동"}
                """;

        mockMvc.perform(put(BASE_ENDPOINT + "/1/account")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
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
        return body.get("data").get("accessToken").asText();
    }
}
