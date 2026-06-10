package com.chunbaetour.domain.payment.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.auth.dto.LoginRequest;
import com.chunbaetour.domain.auth.dto.SignupRequest;
import com.chunbaetour.domain.payment.entity.PaymentOrder;
import com.chunbaetour.domain.payment.entity.QrPayRequest;
import com.chunbaetour.domain.payment.repository.PaymentOrderRepository;
import com.chunbaetour.domain.payment.repository.QrPayRequestRepository;
import com.chunbaetour.domain.payment.type.PaymentMethod;
import com.chunbaetour.domain.payment.type.PaymentOrderStatus;
import com.chunbaetour.domain.payment.type.QrPayStatus;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import com.chunbaetour.domain.yeopjeon.repository.WalletRepository;
import java.time.LocalDateTime;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 사용자 직접 취소 API 통합 테스트 (KAN-252).
 * - POST /api/v1/payments/{orderId}/cancel — PENDING 충전 주문 취소
 * - POST /api/v1/payments/qr/{payRequestId}/cancel — PENDING QR 결제 요청 취소
 *
 * <p>DB ENUM 영속(특히 qr_pay_requests.status CANCELLED 마이그레이션)·소유권·상태 가드·보안을 end-to-end로 검증.
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
class PaymentCancelIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "cancel-user@example.com";
    private static final String OTHER_EMAIL = "cancel-other@example.com";
    private static final String PASSWORD = "Pa$$w0rd1!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AccountRepository accountRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private PaymentOrderRepository paymentOrderRepository;
    @Autowired private QrPayRequestRepository qrPayRequestRepository;
    @Autowired private StringRedisTemplate redis;

    @AfterEach
    void cleanup() {
        qrPayRequestRepository.deleteAll();
        paymentOrderRepository.deleteAll();
        walletRepository.deleteAll();
        accountRepository.deleteAll();
        deleteKeysByScan("auth:refresh:*");
        deleteKeysByScan("auth:blacklist:*");
        deleteKeysByScan("idempotency:*");
    }

    // ── 충전 주문 취소 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("본인 PENDING 충전 주문 취소 → 204 + DB status=CANCELLED")
    void cancelCharge_pending_success() throws Exception {
        signup(EMAIL, "취소유저");
        String token = login(EMAIL);
        Long userId = extractUserId(token);
        paymentOrderRepository.save(PaymentOrder.create(
                "uid-pending", userId, 10_000L, "idem-pending", PaymentMethod.CARD, "uid-pending"));

        mockMvc.perform(post("/api/v1/payments/uid-pending/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        PaymentOrder cancelled = paymentOrderRepository.findByOrderUid("uid-pending").orElseThrow();
        Assertions.assertThat(cancelled.getStatus()).isEqualTo(PaymentOrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("완료(COMPLETED) 충전 주문 취소 → 409 PAY_021 (대기 중만 취소 가능)")
    void cancelCharge_completed_conflict() throws Exception {
        signup(EMAIL, "취소유저");
        String token = login(EMAIL);
        Long userId = extractUserId(token);
        PaymentOrder order = PaymentOrder.create(
                "uid-done", userId, 10_000L, "idem-done", PaymentMethod.CARD, "uid-done");
        ReflectionTestUtils.setField(order, "status", PaymentOrderStatus.COMPLETED);
        paymentOrderRepository.save(order);

        mockMvc.perform(post("/api/v1/payments/uid-done/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAY_021"));
    }

    @Test
    @DisplayName("타인 충전 주문 취소 → 404 PAY_009 (존재 숨김)")
    void cancelCharge_otherUser_notFound() throws Exception {
        signup(OTHER_EMAIL, "타인");
        String otherToken = login(OTHER_EMAIL);
        Long otherUserId = extractUserId(otherToken);
        paymentOrderRepository.save(PaymentOrder.create(
                "uid-other", otherUserId, 10_000L, "idem-other", PaymentMethod.CARD, "uid-other"));

        signup(EMAIL, "취소유저");
        String token = login(EMAIL);

        mockMvc.perform(post("/api/v1/payments/uid-other/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAY_009"));
    }

    @Test
    @DisplayName("토큰 없이 충전 주문 취소 → 401")
    void cancelCharge_noToken_unauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/payments/uid-any/cancel"))
                .andExpect(status().isUnauthorized());
    }

    // ── QR 결제 요청 취소 (qr_pay_requests.status CANCELLED 마이그레이션 검증 포함) ──

    @Test
    @DisplayName("본인 PENDING QR 결제 요청 취소 → 200 + DB status=CANCELLED + pendingKey 해제")
    void cancelQrPay_pending_success() throws Exception {
        signup(EMAIL, "취소유저");
        String token = login(EMAIL);
        Long userId = extractUserId(token);
        QrPayRequest seeded = qrPayRequestRepository.save(QrPayRequest.create(
                "qr-pending", userId, 777L, 5_000L, "[]", LocalDateTime.now().plusMinutes(5)));

        mockMvc.perform(post("/api/v1/payments/qr/qr-pending/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        QrPayRequest cancelled = qrPayRequestRepository.findById(seeded.getId()).orElseThrow();
        Assertions.assertThat(cancelled.getStatus()).isEqualTo(QrPayStatus.CANCELLED);
        Assertions.assertThat(cancelled.getPendingKey()).isNull(); // unique 해제 → 재결제 가능
    }

    @Test
    @DisplayName("이미 EXPIRED인 QR 결제 요청 취소 → 409 PAY_025 (상태 가드)")
    void cancelQrPay_expired_conflict() throws Exception {
        signup(EMAIL, "취소유저");
        String token = login(EMAIL);
        Long userId = extractUserId(token);
        QrPayRequest req = QrPayRequest.create(
                "qr-expired", userId, 777L, 5_000L, "[]", LocalDateTime.now().plusMinutes(5));
        ReflectionTestUtils.setField(req, "status", QrPayStatus.EXPIRED);
        ReflectionTestUtils.setField(req, "pendingKey", null); // EXPIRED는 pendingKey null
        qrPayRequestRepository.save(req);

        mockMvc.perform(post("/api/v1/payments/qr/qr-expired/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAY_025"));
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────────

    private void deleteKeysByScan(String pattern) {
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
        Set<String> keys = new HashSet<>();
        try (var cursor = redis.scan(options)) {
            cursor.forEachRemaining(keys::add);
        }
        if (!keys.isEmpty()) redis.delete(keys);
    }

    private void signup(String email, String nickname) throws Exception {
        SignupRequest request = new SignupRequest(email, PASSWORD, nickname);
        mockMvc.perform(post("/api/v1/users/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private String login(String email) throws Exception {
        LoginRequest request = new LoginRequest(email, PASSWORD);
        MvcResult result = mockMvc.perform(post("/api/v1/users/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("data").get("accessToken").asText();
    }

    private Long extractUserId(String token) {
        String[] parts = token.split("\\.");
        String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
        try {
            return objectMapper.readTree(payload).get("sub").asLong();
        } catch (Exception e) {
            throw new RuntimeException("JWT payload sub 파싱 실패", e);
        }
    }
}
