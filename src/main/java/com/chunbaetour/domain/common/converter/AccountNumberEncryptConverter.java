package com.chunbaetour.domain.common.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 정산 계좌번호 AES-128 암호화 컨버터.
 *
 * <p>DB 덤프·내부 직접 조회로 계좌번호가 노출되는 것을 방지한다.
 * 저장 시 암호문(Base64), 조회 시 자동 복호화 — 서비스 코드 변경 없음.
 *
 * <p>키 관리: {@code ACCOUNT_ENCRYPTION_KEY} 환경변수로 주입. 반드시 16자(AES-128).
 * 운영 배포 시 환경변수로 교체 필수 — 기본값은 로컬 개발 전용.
 *
 * <p>기존 평문 데이터가 있는 경우 복호화 실패 → 별도 마이그레이션 스크립트 필요.
 */
@Converter
@Component
public class AccountNumberEncryptConverter implements AttributeConverter<String, String> {

    private static final String ALGORITHM = "AES/ECB/PKCS5Padding";

    @Value("${app.encryption.account-key}")
    private String secretKey;

    /** 평문 계좌번호 → AES 암호화 후 Base64 인코딩 */
    @Override
    public String convertToDatabaseColumn(String plainText) {
        if (plainText == null) {
            return null;
        }
        try {
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            return Base64.getEncoder().encodeToString(
                    cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("계좌번호 암호화 실패", e);
        }
    }

    /** Base64 암호문 → AES 복호화 후 평문 반환 */
    @Override
    public String convertToEntityAttribute(String cipherText) {
        if (cipherText == null) {
            return null;
        }
        try {
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            return new String(
                    cipher.doFinal(Base64.getDecoder().decode(cipherText)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("계좌번호 복호화 실패", e);
        }
    }
}
