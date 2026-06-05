package com.chunbaetour.domain.common.converter;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 정산 계좌번호 AES-128-GCM 암호화 컨버터.
 *
 * <p>DB 덤프·내부 직접 조회로 계좌번호가 노출되는 것을 방지한다.
 * 저장 시 "v2:<base64(iv+ciphertext+tag)>", 조회 시 자동 복호화 — 서비스 코드 변경 없음.
 *
 * <p>포맷: {@code v2:<base64>} — IV(12바이트) + 암호문 + GCM 태그(16바이트)를 합쳐 Base64 인코딩.
 * ECB 대비 무작위 IV로 동일 평문도 매번 다른 암호문 저장, 태그로 무결성 보장.
 *
 * <p>레거시 호환: {@code v2:} prefix 없는 값은 평문으로 간주하고 그대로 반환 (점진 전환).
 * 다음 updateAccount() 호출 시 GCM 암호화되어 재저장됨.
 *
 * <p>키 관리: {@code ACCOUNT_ENCRYPTION_KEY} 환경변수로 주입. UTF-8 기준 반드시 16바이트(AES-128).
 * 운영 배포 시 환경변수로 교체 필수 — 기본값은 로컬 개발 전용.
 */
@Converter
@Component
public class AccountNumberEncryptConverter implements AttributeConverter<String, String> {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    // v2: prefix — 포맷 버전 식별자. 레거시 평문(prefix 없음)과 구분.
    private static final String CIPHER_PREFIX = "v2:";
    // OS entropy 소모 최소화 — 인스턴스 생성 비용이 높으므로 클래스 레벨 공유
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${app.encryption.account-key}")
    private String secretKey;

    // 매 호출마다 SecretKeySpec 재생성 방지 — secretKey는 부팅 후 불변
    private SecretKeySpec cachedKeySpec;

    @PostConstruct
    public void init() {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("app.encryption.account-key가 비어 있습니다.");
        }
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length != 16) {
            throw new IllegalStateException("app.encryption.account-key는 UTF-8 기준 정확히 16바이트여야 합니다.");
        }
        this.cachedKeySpec = new SecretKeySpec(keyBytes, "AES");
    }

    /** 평문 계좌번호 → AES-GCM 암호화 후 "v2:<base64(iv+ciphertext+tag)>" 반환 */
    @Override
    public String convertToDatabaseColumn(String plainText) {
        if (plainText == null) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, cachedKeySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return CIPHER_PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("계좌번호 암호화 실패", e);
        }
    }

    /**
     * DB 값 → 평문 계좌번호 반환.
     * "v2:" prefix 있음 → GCM 복호화. prefix 없음 → 레거시 평문 그대로 반환.
     */
    @Override
    public String convertToEntityAttribute(String stored) {
        if (stored == null) {
            return null;
        }
        if (!stored.startsWith(CIPHER_PREFIX)) {
            // 레거시 평문 — updateAccount() 다음 호출 시 암호화되어 재저장됨
            return stored;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(stored.substring(CIPHER_PREFIX.length()));
            byte[] iv = Arrays.copyOfRange(combined, 0, GCM_IV_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(combined, GCM_IV_LENGTH, combined.length);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, cachedKeySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("계좌번호 복호화 실패", e);
        }
    }
}
