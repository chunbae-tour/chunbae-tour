package com.chunbaetour.domain.auth.security;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS(Cross-Origin Resource Sharing) 정책 설정.
 *
 * <p>Refresh Token을 HttpOnly Cookie로 전송하려면 브라우저가 cross-origin 요청에 자격증명(쿠키)을
 * 함께 보내도록 허용해야 한다. 그러기 위한 필수 조건:
 * <ul>
 *   <li>{@code Access-Control-Allow-Credentials: true}</li>
 *   <li>{@code Access-Control-Allow-Origin}이 와일드카드(*)가 아니라 명시적 origin</li>
 * </ul>
 * 따라서 {@link #allowedOrigins()}는 와일드카드 금지, 명시적 origin 리스트만 허용한다.
 *
 * <p>적용 prefix: {@code cors}
 *
 * @param allowedOrigins   허용된 origin 리스트 (스킴+호스트+포트). 예: {@code http://localhost:3000}.
 *                         프로파일별로 다르게 설정 (local=개발 서버, prod=실제 도메인).
 * @param allowedMethods   허용된 HTTP 메서드. OPTIONS는 Preflight용으로 반드시 포함.
 * @param allowedHeaders   허용된 요청 헤더. Authorization(Access Token) + Content-Type(JSON) + Accept 최소 필요.
 * @param allowCredentials 자격증명(쿠키, Authorization) 전송 허용. Refresh Cookie 흐름에서 true 필수.
 * @param maxAge           Preflight 응답 캐싱 시간. 너무 길면 정책 변경 반영 지연, 너무 짧으면 Preflight 요청 폭증.
 */
@ConfigurationProperties(prefix = "cors")
public record CorsProperties(
        List<String> allowedOrigins,
        List<String> allowedMethods,
        List<String> allowedHeaders,
        boolean allowCredentials,
        Duration maxAge
) {

    /**
     * 정적 검증:
     * <ul>
     *   <li>allowed-origins 비어있으면 부팅 실패 (정책 명시 강제)</li>
     *   <li>{@code allowCredentials=true} + 와일드카드 origin 조합은 브라우저가 거부하므로 부팅 차단</li>
     * </ul>
     */
    public CorsProperties {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            throw new IllegalStateException("cors.allowed-origins 가 비어 있습니다.");
        }
        if (allowCredentials && allowedOrigins.contains("*")) {
            throw new IllegalStateException(
                    "cors.allow-credentials=true 와 cors.allowed-origins=* 는 동시에 사용할 수 없습니다 (브라우저가 거부함).");
        }
        if (allowedMethods == null || allowedMethods.isEmpty()) {
            throw new IllegalStateException("cors.allowed-methods 가 비어 있습니다.");
        }
        if (allowedHeaders == null || allowedHeaders.isEmpty()) {
            throw new IllegalStateException("cors.allowed-headers 가 비어 있습니다.");
        }
        if (maxAge == null || maxAge.isNegative()) {
            throw new IllegalStateException("cors.max-age 가 유효하지 않습니다.");
        }
    }
}
