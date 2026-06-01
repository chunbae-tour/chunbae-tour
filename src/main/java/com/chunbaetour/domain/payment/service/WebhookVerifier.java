package com.chunbaetour.domain.payment.service;

import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.payment.config.PortOneProperties;
import com.chunbaetour.domain.payment.exception.PaymentException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WebhookVerifier {

    private static final long MAX_AGE_SECONDS = 300L;
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final PortOneProperties properties;

    public void verify(String webhookId, String signatureHeader, String timestamp, String rawPayload) {
        verifyTimestamp(timestamp);
        verifySignature(webhookId, signatureHeader, timestamp, rawPayload);
    }

    private void verifyTimestamp(String timestamp) {
        long webhookTime;
        try {
            webhookTime = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            throw new PaymentException(ErrorCode.WEBHOOK_SIGNATURE_INVALID);
        }
        if (Math.abs(Instant.now().getEpochSecond() - webhookTime) > MAX_AGE_SECONDS) {
            throw new PaymentException(ErrorCode.WEBHOOK_SIGNATURE_INVALID);
        }
    }

    private void verifySignature(String webhookId, String signatureHeader, String timestamp, String rawPayload) {
        String message = webhookId + "." + timestamp + "." + rawPayload;
        String computed = computeHmac(message);

        // 헤더 형식: "v1,{base64}" 또는 공백 구분 다중 서명
        boolean matched = Arrays.stream(signatureHeader.split(" "))
                .filter(s -> s.startsWith("v1,"))
                .map(s -> s.substring(3))
                .anyMatch(s -> MessageDigest.isEqual(
                        s.getBytes(StandardCharsets.UTF_8),
                        computed.getBytes(StandardCharsets.UTF_8)));

        if (!matched) {
            throw new PaymentException(ErrorCode.WEBHOOK_SIGNATURE_INVALID);
        }
    }

    private String computeHmac(String message) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(webhookSecretBytes(), HMAC_ALGORITHM));
            return Base64.getEncoder().encodeToString(
                    mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new PaymentException(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE);
        }
    }
    // PortOne 웹훅 시크릿을 HMAC 서명 검증용 바이트 배열로 변환
    // "whsec_" 접두사가 있으면 Base64 디코딩, 없으면 UTF-8 바이트로 반환
    private byte[] webhookSecretBytes() {
        String secret = properties.getWebhookSecret();
        if (secret != null && secret.startsWith("whsec_")) {
            return Base64.getDecoder().decode(secret.substring("whsec_".length()));
        }
        return secret.getBytes(StandardCharsets.UTF_8);
    }
}
