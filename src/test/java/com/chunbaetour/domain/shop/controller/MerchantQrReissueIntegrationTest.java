package com.chunbaetour.domain.shop.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.auth.Role;
import com.chunbaetour.domain.auth.dto.LoginRequest;
import com.chunbaetour.domain.auth.dto.SignupRequest;
import com.chunbaetour.domain.auth.jwt.TokenIssuer;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import com.chunbaetour.domain.yeopjeon.repository.WalletRepository;
import java.util.HashSet;
import java.util.Set;
import org.assertj.core.api.Assertions;
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
 * POST /api/v1/merchants/me/shops/{shopId}/qr/reissue 통합 테스트 (KAN-253).
 * QR 재발급 — 보안/소유권/상태정책 + nonce 회전(옛 QR 무효화) end-to-end 검증.
 * (shops.qr_nonce 마이그레이션 영속도 함께 검증)
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
class MerchantQrReissueIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "qrreissue@example.com";
    private static final String PASSWORD = "Pa$$w0rd1!";
    private static final String NICKNAME = "QR재발급유저";
    private static final String BASE = "/api/v1/merchants/me/shops";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AccountRepository accountRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private ShopRepository shopRepository;
    @Autowired private TokenIssuer tokenIssuer;
    @Autowired private StringRedisTemplate redis;

    @AfterEach
    void cleanup() {
        shopRepository.deleteAll();
        walletRepository.deleteAll();
        accountRepository.deleteAll();
        deleteKeysByScan("auth:refresh:*");
        deleteKeysByScan("auth:blacklist:*");
    }

    @Test
    @DisplayName("MERCHANT 본인 가게 재발급 → 200 + payload nonce 회전 + DB qrNonce 변경")
    void reissue_success() throws Exception {
        Long merchantUserId = 9101L;
        String token = tokenIssuer.issueAccess(merchantUserId, Role.MERCHANT, "m1@test.com");
        Shop shop = shopRepository.save(seedShop(merchantUserId, 9101L));
        String oldNonce = shop.getQrNonce();

        MvcResult result = mockMvc.perform(post(BASE + "/" + shop.getId() + "/qr/reissue")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.shopId").value(shop.getId()))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        String newPayload = data.get("qrPayload").asText();
        Shop reloaded = shopRepository.findById(shop.getId()).orElseThrow();

        Assertions.assertThat(reloaded.getQrNonce()).isNotEqualTo(oldNonce); // 회전됨
        Assertions.assertThat(newPayload).isEqualTo("YEOPJEON_PAY:SHOP:" + shop.getId() + ":" + reloaded.getQrNonce());
        Assertions.assertThat(newPayload).doesNotContain(oldNonce); // 옛 nonce 미포함 → 옛 QR 무효
    }

    @Test
    @DisplayName("MERCHANT 타인 가게 재발급 → 404 SHOP_001")
    void reissue_otherShop_notFound() throws Exception {
        String token = tokenIssuer.issueAccess(9102L, Role.MERCHANT, "m2@test.com");

        mockMvc.perform(post(BASE + "/99999/qr/reissue")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHOP_001"));
    }

    @Test
    @DisplayName("USER 토큰으로 재발급 → 403 AUTH_007")
    void reissue_userToken_forbidden() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME);
        String userToken = login(EMAIL, PASSWORD);

        mockMvc.perform(post(BASE + "/1/qr/reissue")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_007"));
    }

    @Test
    @DisplayName("토큰 없이 재발급 → 401 AUTH_006")
    void reissue_noToken_unauthorized() throws Exception {
        mockMvc.perform(post(BASE + "/1/qr/reissue"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_006"));
    }

    @Test
    @DisplayName("SUSPENDED 가게 재발급 → 403 SHOP_005 (정지/폐업 차단 정책)")
    void reissue_suspendedShop_forbidden() throws Exception {
        Long merchantUserId = 9103L;
        String token = tokenIssuer.issueAccess(merchantUserId, Role.MERCHANT, "m3@test.com");
        Shop shop = seedShop(merchantUserId, 9103L);
        shop.hide(); // ACTIVE → SUSPENDED
        shopRepository.save(shop);

        mockMvc.perform(post(BASE + "/" + shop.getId() + "/qr/reissue")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SHOP_005"));
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────────

    private Shop seedShop(Long userId, Long applicationId) {
        return Shop.builder()
                .userId(userId).applicationId(applicationId)
                .shopName("재발급테스트가게").category("FOOD")
                .address("서울시 강남구").phone("02-0000-0000")
                .description("테스트").build();
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
