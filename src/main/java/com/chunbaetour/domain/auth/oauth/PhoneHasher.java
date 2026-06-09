package com.chunbaetour.domain.auth.oauth;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 전화번호 중복가입 판별용 결정적 해시 — HMAC-SHA256(정규화 전화번호).
 *
 * <p>전화번호 원문(phone)은 AES-GCM 암호화 저장(랜덤 IV라 동일 평문도 매번 다른 암호문 → UNIQUE/동등비교
 * 불가)이라, 중복 판별은 별도 결정적 해시 컬럼({@code phone_hash})에 UNIQUE를 걸어 수행한다.
 *
 * <p>단순 SHA-256은 전화번호 공간(~10^9)이 작아 DB 덤프 시 역추적(레인보우/브루트포스)이 쉬우므로,
 * 비밀키({@code app.encryption.account-key}) 기반 HMAC으로 키가 없으면 원문을 역추적할 수 없게 한다.
 * (암호화 키를 재사용하지만 HMAC 용도라 알고리즘 분리됨.)
 */
@Component
public class PhoneHasher {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public PhoneHasher(@Value("${app.encryption.account-key}") String secret) {
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    /**
     * 정규화(숫자만)된 전화번호의 HMAC-SHA256 16진 문자열(64자) 반환.
     *
     * @param normalizedPhone 숫자만 남긴 전화번호 (호출자가 정규화)
     */
    public String hash(String normalizedPhone) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte[] digest = mac.doFinal(normalizedPhone.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
