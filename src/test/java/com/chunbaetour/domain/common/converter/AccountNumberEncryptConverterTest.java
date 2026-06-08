package com.chunbaetour.domain.common.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AccountNumberEncryptConverterTest {

    private AccountNumberEncryptConverter converter;

    // AES-128: UTF-8 기준 정확히 16바이트
    private static final String TEST_KEY = "TestKey1234567890".substring(0, 16);

    @BeforeEach
    void setUp() {
        converter = new AccountNumberEncryptConverter();
        ReflectionTestUtils.setField(converter, "secretKey", TEST_KEY);
        converter.init();
    }

    @Test
    @DisplayName("암호화 → 복호화 라운드트립: 원본 평문이 복원된다")
    void roundtrip_encryptThenDecrypt_returnsOriginal() {
        String plainText = "1234567890";

        String encrypted = converter.convertToDatabaseColumn(plainText);
        String decrypted = converter.convertToEntityAttribute(encrypted);

        assertThat(decrypted).isEqualTo(plainText);
    }

    @Test
    @DisplayName("암호화 결과는 'v2:' prefix를 포함한다")
    void encrypted_hasV2Prefix() {
        String encrypted = converter.convertToDatabaseColumn("1234567890");

        assertThat(encrypted).startsWith("v2:");
    }

    @Test
    @DisplayName("동일 평문을 두 번 암호화하면 서로 다른 암호문이 생성된다 (랜덤 IV)")
    void sameInput_producedDifferentCiphertext_eachTime() {
        String plainText = "1234567890";

        String first = converter.convertToDatabaseColumn(plainText);
        String second = converter.convertToDatabaseColumn(plainText);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("레거시 평문(v2: prefix 없음)은 그대로 반환된다")
    void legacyPlainText_withoutPrefix_returnedAsIs() {
        String legacyPlain = "123-45-67890";

        String result = converter.convertToEntityAttribute(legacyPlain);

        assertThat(result).isEqualTo(legacyPlain);
    }

    @Test
    @DisplayName("null 입력 시 null 반환 — 암호화/복호화 모두")
    void nullInput_returnsNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    @DisplayName("init() — 빈 키 설정 시 IllegalStateException 발생")
    void init_blankKey_throwsIllegalStateException() {
        AccountNumberEncryptConverter c = new AccountNumberEncryptConverter();
        ReflectionTestUtils.setField(c, "secretKey", "");

        assertThatThrownBy(c::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("비어 있습니다");
    }

    @Test
    @DisplayName("init() — 16바이트 미달 키 설정 시 IllegalStateException 발생")
    void init_shortKey_throwsIllegalStateException() {
        AccountNumberEncryptConverter c = new AccountNumberEncryptConverter();
        ReflectionTestUtils.setField(c, "secretKey", "short");

        assertThatThrownBy(c::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("16바이트");
    }

    @Test
    @DisplayName("잘못된 키로 복호화 시 IllegalStateException 발생")
    void wrongKey_decryptThrowsIllegalStateException() {
        String encrypted = converter.convertToDatabaseColumn("1234567890");

        AccountNumberEncryptConverter wrongKeyConverter = new AccountNumberEncryptConverter();
        ReflectionTestUtils.setField(wrongKeyConverter, "secretKey", "WrongKey12345678");
        wrongKeyConverter.init();

        assertThatThrownBy(() -> wrongKeyConverter.convertToEntityAttribute(encrypted))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("복호화 실패");
    }

    @Test
    @DisplayName("하이픈 포함 계좌번호도 라운드트립 정상 처리")
    void hyphenatedAccountNumber_roundtripSuccess() {
        String plainText = "123-456-789012";

        String decrypted = converter.convertToEntityAttribute(
                converter.convertToDatabaseColumn(plainText));

        assertThat(decrypted).isEqualTo(plainText);
    }

    @Test
    @DisplayName("GCM 인증 태그 검증: 암호문 변조 시 IllegalStateException 발생")
    void tamperedCiphertext_throwsIllegalStateException() {
        String encrypted = converter.convertToDatabaseColumn("1234567890");

        // v2: 이후 Base64 디코딩 → 암호문 1바이트 반전 → 재인코딩
        byte[] combined = java.util.Base64.getDecoder()
                .decode(encrypted.substring("v2:".length()));
        // IV는 앞 12바이트 — 암호문 영역(12번째 이후) 첫 바이트 변조
        combined[12] ^= 0xFF;
        String tampered = "v2:" + java.util.Base64.getEncoder().encodeToString(combined);

        assertThatThrownBy(() -> converter.convertToEntityAttribute(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("복호화 실패");
    }

    @Test
    @DisplayName("v2: prefix 뒤 Base64가 손상된 경우 IllegalStateException 발생")
    void malformedBase64AfterPrefix_throwsIllegalStateException() {
        String malformed = "v2:!!!invalid-base64-data!!!";

        assertThatThrownBy(() -> converter.convertToEntityAttribute(malformed))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("복호화 실패");
    }
}
