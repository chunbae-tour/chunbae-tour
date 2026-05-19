package com.chunbaetour.domain.auth.security;

import com.chunbaetour.domain.auth.jwt.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Refresh Token을 담은 {@link ResponseCookie}를 만드는 공장.
 *
 * <p>설정값(이름, Secure, SameSite, Path)은 {@link CookieProperties}에서, 만료 기간은
 * {@link JwtProperties#refreshTokenTtl()}와 일치시켜 토큰 만료와 쿠키 만료가 어긋나지 않도록 한다.
 *
 * <p>전역에서 동일한 정책으로 쿠키를 발급/제거해야 일관된 동작이 보장되므로 본 클래스를 단일 진입점으로 사용한다.
 * (LoginService, ReissueService, LogoutService 모두 여기를 거친다.)
 */
@Component
@RequiredArgsConstructor
public class RefreshCookieFactory {

    private final CookieProperties cookieProperties;
    private final JwtProperties jwtProperties;

    /**
     * Refresh Token 값을 담은 새 쿠키를 만든다.
     *
     * <p>발급 시 사용. 만료 시각은 Refresh Token TTL(보통 7일)과 동일.
     *
     * @param refreshToken JWT Refresh Token 문자열
     * @return Set-Cookie 헤더에 그대로 넣을 수 있는 ResponseCookie
     */
    public ResponseCookie create(String refreshToken) {
        return baseBuilder()
                .value(refreshToken)
                .maxAge(jwtProperties.refreshTokenTtl())
                .build();
    }

    /**
     * 클라이언트의 Refresh 쿠키를 즉시 제거하는 만료 쿠키를 만든다.
     *
     * <p>로그아웃 흐름(S4) 또는 강제 무효화 시 사용 예정. value는 빈 문자열,
     * maxAge는 0으로 설정하여 브라우저가 즉시 쿠키를 삭제하도록 한다.
     *
     * <p>주의: 동일한 {@code name} + {@code path}여야 브라우저가 같은 쿠키로 인식해 덮어쓴다.
     * 그래서 모든 속성은 발급 시와 일치시켜야 한다 (baseBuilder 재사용 이유).
     */
    public ResponseCookie expire() {
        return baseBuilder()
                .value("")
                .maxAge(0)
                .build();
    }

    /**
     * 공통 속성(이름, Secure, SameSite, Path, HttpOnly)을 채운 빌더.
     * value/maxAge만 호출자가 다르게 채우면 된다.
     */
    private ResponseCookie.ResponseCookieBuilder baseBuilder() {
        return ResponseCookie.from(cookieProperties.name(), "")
                // HttpOnly: JavaScript에서 document.cookie로 읽지 못함 → XSS 방어 핵심
                .httpOnly(true)
                // Secure: HTTPS에서만 전송. local=false, prod=true (프로파일별 분리)
                .secure(cookieProperties.secure())
                // SameSite: cross-site 요청에서의 쿠키 전송 정책. Lax 기본 (CSRF 방어 + SPA 호환)
                .sameSite(cookieProperties.sameSite())
                // Path: 쿠키 전송 범위 제한. /api/v1/auth로 좁혀 다른 API에는 노출 안 됨
                .path(cookieProperties.path());
    }
}
