package com.chunbaetour.domain.auth.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chunbaetour.domain.auth.security.CookieProperties.SameSite;
import org.junit.jupiter.api.Test;

/**
 * {@link CookieProperties} compact constructor 검증.
 *
 * <p>핵심 회귀 가드 (KAN-125 S5):
 * <ul>
 *   <li>name / sameSite / path 누락 시 부팅 실패</li>
 *   <li>{@code SameSite=None} + {@code Secure=false} 조합 부팅 실패 (브라우저 요구사항)</li>
 *   <li>Lax/Strict + Secure 어느 값 조합도 통과 (local/prod 양쪽 호환)</li>
 * </ul>
 */
class CookiePropertiesTest {

    @Test
    void name_누락_부팅_실패() {
        assertThatThrownBy(() -> new CookieProperties("", false, SameSite.LAX, "/api/v1/auth"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("name");
    }

    @Test
    void sameSite_null_부팅_실패() {
        assertThatThrownBy(() -> new CookieProperties("refreshToken", false, null, "/api/v1/auth"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("same-site");
    }

    @Test
    void path_누락_부팅_실패() {
        assertThatThrownBy(() -> new CookieProperties("refreshToken", false, SameSite.LAX, ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("path");
    }

    @Test
    void SameSite_None_과_Secure_false_조합_부팅_실패() {
        // 브라우저(Chrome 80+ 등)가 cookie 자체를 무시 → 로그인 silent 실패 → 운영 사고.
        // 부팅 단계에서 차단.
        assertThatThrownBy(() -> new CookieProperties("refreshToken", false, SameSite.NONE, "/api/v1/auth"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("same-site=None")
                .hasMessageContaining("secure=true");
    }

    @Test
    void SameSite_None_과_Secure_true_조합_통과() {
        // 운영 cross-site 배치 시 정상 조합 (HTTPS + 브라우저 요구사항 충족).
        assertThatCode(() -> new CookieProperties("refreshToken", true, SameSite.NONE, "/api/v1/auth"))
                .doesNotThrowAnyException();
    }

    @Test
    void SameSite_Lax_과_Secure_false_통과() {
        // 로컬 개발 default (HTTP).
        assertThatCode(() -> new CookieProperties("refreshToken", false, SameSite.LAX, "/api/v1/auth"))
                .doesNotThrowAnyException();
    }

    @Test
    void SameSite_Lax_과_Secure_true_통과() {
        // prod same-site 배치 default.
        assertThatCode(() -> new CookieProperties("refreshToken", true, SameSite.LAX, "/api/v1/auth"))
                .doesNotThrowAnyException();
    }

    @Test
    void SameSite_Strict_과_Secure_true_통과() {
        // Strict는 일반 SPA에 비권장이지만 코드 차원에서 허용은 함.
        assertThatCode(() -> new CookieProperties("refreshToken", true, SameSite.STRICT, "/api/v1/auth"))
                .doesNotThrowAnyException();
    }

    @Test
    void SameSite_enum_headerValue_표준_표기() {
        // ResponseCookie.sameSite(String)이 요구하는 첫 글자 대문자 표기.
        // 변경되면 RefreshCookieFactory가 잘못된 Set-Cookie 헤더를 발급 → 브라우저가 cookie 무시.
        org.junit.jupiter.api.Assertions.assertEquals("Lax", SameSite.LAX.headerValue());
        org.junit.jupiter.api.Assertions.assertEquals("Strict", SameSite.STRICT.headerValue());
        org.junit.jupiter.api.Assertions.assertEquals("None", SameSite.NONE.headerValue());
    }
}
