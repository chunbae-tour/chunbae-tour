package com.chunbaetour.domain.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Refresh Token을 HttpOnly Cookie로 전달할 때 사용하는 설정값 묶음.
 *
 * <p>local / prod 프로파일별 yml에서 값을 주입받는다.
 * 특히 {@code secure}는 local=false (http 허용), prod=true (https 강제)로 분리한다.
 *
 * <p>적용 prefix: {@code cookie.refresh-token}
 *
 * @param name      Cookie 이름. 클라이언트가 쿠키 자동 전송 시 키로 사용. 변경하면 클라이언트도 같이 수정 필요.
 * @param secure    Secure 플래그. HTTPS 환경에서만 쿠키 전송 허용. prod에서 반드시 true.
 * @param sameSite  SameSite 정책. {@code Lax} 권장 (CSRF 기본 방어 + SPA 호환).
 *                  {@code Strict}는 외부 링크 클릭 시 쿠키 누락되어 UX 깨짐.
 *                  {@code None}은 cross-site 허용 (필요 시 Secure 필수).
 * @param path      쿠키가 전송될 경로 prefix. {@code /api/v1/auth}로 좁히면 다른 API 호출에는 쿠키가 안 실려 대역폭/노출 최소화.
 */
@ConfigurationProperties(prefix = "cookie.refresh-token")
public record CookieProperties(String name, boolean secure, String sameSite, String path) {

    /**
     * 정적 검증: 필수 값이 비어 있거나 의미 없는 값이면 부팅 실패시킨다 (운영 사고 방지).
     */
    public CookieProperties {
        if (name == null || name.isBlank()) {
            throw new IllegalStateException("cookie.refresh-token.name 가 비어 있습니다.");
        }
        if (sameSite == null || sameSite.isBlank()) {
            throw new IllegalStateException("cookie.refresh-token.same-site 가 비어 있습니다.");
        }
        if (path == null || path.isBlank()) {
            throw new IllegalStateException("cookie.refresh-token.path 가 비어 있습니다.");
        }
    }
}
