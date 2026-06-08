package com.chunbaetour.domain.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Refresh Token을 HttpOnly Cookie로 전달할 때 사용하는 설정값 묶음.
 *
 * <p>local / prod 프로파일별 yml에서 값을 주입받는다. 특히 {@code secure}는
 * local=false (http 허용), prod=true (https 강제)로 분리한다.
 *
 * <p>적용 prefix: {@code cookie.refresh-token}
 *
 * <p><b>KAN-125 (S5) 정책 결정</b>: SameSite/도메인 정책은 운영 인프라(프론트엔드/백엔드 도메인 구성)에
 * 의존한다. 본 record는 컴파일/부팅 시점 정합 검증만 제공하고, 실제 운영 값은 환경변수
 * ({@code COOKIE_SAMESITE}) + ADR/{@code docs/operations/samesite-policy.md}에서 결정한다.
 *
 * @param name      Cookie 이름. 클라이언트가 쿠키 자동 전송 시 키로 사용. 변경하면 클라이언트도 같이 수정 필요.
 * @param secure    Secure 플래그. HTTPS 환경에서만 쿠키 전송 허용. prod에서 반드시 true.
 *                  {@code SameSite=None}이면 브라우저 요구사항상 반드시 true (compact ctor가 강제).
 * @param sameSite  SameSite 정책 enum. {@link SameSite#LAX} 권장 — same-site 배치(같은 도메인 또는 같은 eTLD+1)에서
 *                  CSRF 기본 방어 + SPA 호환. cross-site 배치라면 {@link SameSite#NONE} + {@code secure=true} 필수.
 * @param path      쿠키가 전송될 경로 prefix. {@code /api/v1/auth}로 좁히면 다른 API 호출에는 쿠키가 안 실려
 *                  대역폭/노출 최소화.
 */
@ConfigurationProperties(prefix = "cookie.refresh-token")
public record CookieProperties(String name, boolean secure, SameSite sameSite, String path) {

    /**
     * SameSite 정책 enum.
     *
     * <p>RFC 6265bis + 브라우저 구현 표준값 (Lax/Strict/None) 외 허용 안 함. Spring Boot의 ConfigurationProperties
     * enum 바인딩이 case-insensitive 매칭을 수행하므로 yml에는 {@code Lax}/{@code lax}/{@code LAX} 어느 형태도 가능.
     *
     * <p>{@link #headerValue()}는 HTTP {@code Set-Cookie} 헤더 표준 표기(첫 글자 대문자)를 반환한다.
     * Spring {@code ResponseCookie.sameSite(String)}이 이 표기를 요구.
     */
    public enum SameSite {
        /**
         * CSRF 기본 방어 + 외부 링크 클릭 시 GET 요청은 허용. SPA 호환 일반 default.
         * same-site (같은 eTLD+1) 배치에 적합. cross-site에서는 cookie 누락.
         */
        LAX("Lax"),

        /**
         * 가장 엄격. cross-site 요청 (외부 링크 클릭 포함) 시 쿠키 미전송.
         * 외부 링크에서 진입 직후 cookie 없으면 UX 깨질 수 있어 일반 SPA에는 비권장.
         */
        STRICT("Strict"),

        /**
         * cross-site cookie 전송 허용. 브라우저 요구사항: {@code Secure=true} 필수.
         * 운영 프론트엔드와 백엔드 도메인이 서로 다른 site일 때 선택.
         */
        NONE("None");

        private final String headerValue;

        SameSite(String headerValue) {
            this.headerValue = headerValue;
        }

        /** HTTP Set-Cookie 표준 표기 (첫 글자 대문자). */
        public String headerValue() {
            return headerValue;
        }
    }

    /**
     * 정적 검증:
     * <ul>
     *   <li>name/path 비어있으면 부팅 실패 (운영 사고 방지)</li>
     *   <li>{@code SameSite=None}은 {@code Secure=true}와만 사용 가능 — 브라우저 요구사항 (Chrome 80+, Safari, Firefox).
     *       이 정합이 깨지면 브라우저가 cookie 자체를 무시해 로그인 흐름 silent 실패 → 운영 사고.</li>
     * </ul>
     *
     * <p>SameSite null 케이스는 ConfigurationProperties 바인딩이 yml에 값을 못 찾으면 발생.
     * yml default 또는 환경변수 fallback을 반드시 두어야 한다 (application.yml의 {@code ${COOKIE_SAMESITE:Lax}}).
     */
    public CookieProperties {
        if (name == null || name.isBlank()) {
            throw new IllegalStateException("cookie.refresh-token.name 가 비어 있습니다.");
        }
        if (sameSite == null) {
            throw new IllegalStateException(
                    "cookie.refresh-token.same-site 가 비어 있습니다. Lax/Strict/None 중 하나로 설정해야 합니다.");
        }
        if (path == null || path.isBlank()) {
            throw new IllegalStateException("cookie.refresh-token.path 가 비어 있습니다.");
        }
        if (sameSite == SameSite.NONE && !secure) {
            // 브라우저 요구사항 — Chrome 80+, Safari, Firefox 모두 SameSite=None은 Secure=true 필수.
            // 이 조합이 운영에 흘러가면 cookie가 silent로 무시되어 로그인이 동작하지 않는 사고가 발생함.
            throw new IllegalStateException(
                    "cookie.refresh-token.same-site=None 은 cookie.refresh-token.secure=true 와만 사용 가능합니다 "
                            + "(브라우저 요구사항). 운영 도메인 정책은 docs/operations/samesite-policy.md 참조.");
        }
    }
}
