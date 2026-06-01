package com.chunbaetour.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.payment.config.PortOneProperties;
import com.chunbaetour.domain.payment.exception.PaymentException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebhookVerifierTest {

    @Mock
    private PortOneProperties properties;

    @InjectMocks
    private WebhookVerifier webhookVerifier;

    private static final String SECRET = "test-webhook-secret";
    private static final String WEBHOOK_ID = "wh_test_123";
    private static final String RAW_BODY = "{\"type\":\"Transaction.Paid\",\"data\":{\"paymentId\":\"order-1\",\"transactionId\":\"tx-1\"}}";

    @BeforeEach
    void setup() {
        given(properties.getWebhookSecret()).willReturn(SECRET);
    }

    private String computeHmac(String webhookId, String timestamp, String body) throws Exception {
        return computeHmac(SECRET.getBytes(StandardCharsets.UTF_8), webhookId, timestamp, body);
    }

    private String computeHmac(byte[] secretBytes, String webhookId, String timestamp, String body) throws Exception {
        String message = webhookId + "." + timestamp + "." + body;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("유효한 서명 → 검증 통과")
    void verify_valid_signature_passes() throws Exception {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String sig = computeHmac(WEBHOOK_ID, timestamp, RAW_BODY);

        assertThatCode(() -> webhookVerifier.verify(WEBHOOK_ID, "v1," + sig, timestamp, RAW_BODY))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("whsec_ 시크릿은 prefix 제거 후 Base64 decode한 키로 검증한다")
    void verify_whsec_secret_decodes_before_hmac() throws Exception {
        byte[] secretBytes = "decoded-webhook-secret".getBytes(StandardCharsets.UTF_8);
        given(properties.getWebhookSecret()).willReturn("whsec_" + Base64.getEncoder().encodeToString(secretBytes));
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String sig = computeHmac(secretBytes, WEBHOOK_ID, timestamp, RAW_BODY);

        assertThatCode(() -> webhookVerifier.verify(WEBHOOK_ID, "v1," + sig, timestamp, RAW_BODY))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("만료된 타임스탬프 (>300s) → PAY_014")
    void verify_expired_timestamp_throws_PAY_014() {
        String oldTimestamp = String.valueOf(Instant.now().getEpochSecond() - 301);

        assertThatThrownBy(() -> webhookVerifier.verify(WEBHOOK_ID, "v1,anysig", oldTimestamp, RAW_BODY))
                .isInstanceOf(PaymentException.class)
                .extracting(ex -> ((PaymentException) ex).getErrorCode())
                .isEqualTo(ErrorCode.WEBHOOK_SIGNATURE_INVALID);
    }

    @Test
    @DisplayName("잘못된 서명 → PAY_014")
    void verify_wrong_signature_throws_PAY_014() {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());

        assertThatThrownBy(() -> webhookVerifier.verify(WEBHOOK_ID, "v1,wrongbase64==", timestamp, RAW_BODY))
                .isInstanceOf(PaymentException.class)
                .extracting(ex -> ((PaymentException) ex).getErrorCode())
                .isEqualTo(ErrorCode.WEBHOOK_SIGNATURE_INVALID);
    }

    @Test
    @DisplayName("여러 서명 중 하나가 일치 → 검증 통과 (키 로테이션 시나리오)")
    void verify_multiple_signatures_one_matches_passes() throws Exception {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String validSig = computeHmac(WEBHOOK_ID, timestamp, RAW_BODY);
        String header = "v1,wrongsig== v1," + validSig;

        assertThatCode(() -> webhookVerifier.verify(WEBHOOK_ID, header, timestamp, RAW_BODY))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("타임스탬프가 숫자 아님 → PAY_014")
    void verify_non_numeric_timestamp_throws_PAY_014() {
        assertThatThrownBy(() -> webhookVerifier.verify(WEBHOOK_ID, "v1,anysig", "not-a-number", RAW_BODY))
                .isInstanceOf(PaymentException.class)
                .extracting(ex -> ((PaymentException) ex).getErrorCode())
                .isEqualTo(ErrorCode.WEBHOOK_SIGNATURE_INVALID);
    }

    @Test
    @DisplayName("v1 접두사 없는 서명만 있음 → PAY_014")
    void verify_no_v1_prefix_throws_PAY_014() throws Exception {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String validSig = computeHmac(WEBHOOK_ID, timestamp, RAW_BODY);

        assertThatThrownBy(() -> webhookVerifier.verify(WEBHOOK_ID, "v2," + validSig, timestamp, RAW_BODY))
                .isInstanceOf(PaymentException.class)
                .extracting(ex -> ((PaymentException) ex).getErrorCode())
                .isEqualTo(ErrorCode.WEBHOOK_SIGNATURE_INVALID);
    }

    @Test
    @DisplayName("경계값: 정확히 300초 전 타임스탬프 → 검증 통과")
    void verify_boundary_300s_passes() throws Exception {
        String timestamp = String.valueOf(Instant.now().getEpochSecond() - 300);
        String sig = computeHmac(WEBHOOK_ID, timestamp, RAW_BODY);

        assertThatCode(() -> webhookVerifier.verify(WEBHOOK_ID, "v1," + sig, timestamp, RAW_BODY))
                .doesNotThrowAnyException();
    }
}
