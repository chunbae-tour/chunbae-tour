package com.chunbaetour.domain.common.ratelimit;

import com.chunbaetour.domain.auth.security.SecurityResponseWriter;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.ratelimit.RateLimitProperties.EndpointPolicy;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * IP 기반 Rate Limit 필터.
 *
 * <p>요청 흐름:
 * <ol>
 *   <li>{@link RateLimitProperties#enabled} = false면 즉시 통과 (로컬 개발/장애 대응 토글)</li>
 *   <li>CORS Preflight (OPTIONS)는 정책 매칭 제외 — 브라우저 사전 검증 요청은 rate limit 대상 아님</li>
 *   <li>요청 method + URI를 정책 리스트와 매칭 → 미매칭이면 통과</li>
 *   <li>매칭된 정책에 대해 {@code ratelimit:{policy-id}:{ip}} 키로 {@link RateLimiter#tryConsume} 호출</li>
 *   <li>허용이면 X-RateLimit-* 헤더 첨부 후 다음 필터로 통과</li>
 *   <li>거부면 AUTH_014 응답 + Retry-After + X-RateLimit-* 헤더 (응답 바디는 ApiResponse 포맷)</li>
 * </ol>
 *
 * <p>설계 결정:
 * <ul>
 *   <li><b>IP 추출 = getRemoteAddr()</b>: 본 슬라이스는 단순. 운영 LB 뒤 배포 시 X-Forwarded-For 신뢰 정책은
 *       Epic B S5 (SameSite/도메인 정책 확정)와 같이 결정. 그 전까지 LB 직접 IP가 보호 대상.</li>
 *   <li><b>JwtAuthenticationFilter 앞 배치</b>: 인증 실패도 rate limit 카운트 대상.
 *       SecurityConfig.addFilterBefore로 등록.</li>
 *   <li><b>정책 미매칭 = 통과</b>: 명시적 정책이 있는 endpoint만 rate limit 적용. 보호 정책 추가는 yml만 수정.</li>
 *   <li><b>X-RateLimit-* 응답 헤더</b>: REST 권장 — 클라이언트가 한도 가까워졌음을 사전 감지하여 적응적 호출 가능.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    /** RFC 6585 표준 헤더. 클라이언트는 본 값(초) 이후 재시도. */
    private static final String HEADER_RETRY_AFTER = HttpHeaders.RETRY_AFTER;
    /** GitHub/Stripe 등 사실상 표준. 정책 한도 안내. */
    private static final String HEADER_RATE_LIMIT_LIMIT = "X-RateLimit-Limit";
    /** 본 요청 처리 후 남은 허용 횟수. */
    private static final String HEADER_RATE_LIMIT_REMAINING = "X-RateLimit-Remaining";

    private final RateLimitProperties properties;
    private final RateLimiter rateLimiter;
    private final SecurityResponseWriter responseWriter;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        // 전역 토글 — 로컬 개발/장애 대응 시 한 번에 비활성화
        if (!properties.enabled()) {
            chain.doFilter(request, response);
            return;
        }

        // CORS Preflight는 사전 검증이라 rate limit 대상 아님 (브라우저가 실제 요청 전 자동 발사)
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        // 매칭되는 정책 탐색 — 첫 번째 매칭이 우선 (Properties.endpoints 순서가 정책 우선순위)
        Optional<EndpointPolicy> matched = matchPolicy(request);
        if (matched.isEmpty()) {
            // 정책 없는 endpoint는 통과 (마이페이지, place 등)
            chain.doFilter(request, response);
            return;
        }

        EndpointPolicy endpoint = matched.get();
        String clientIp = extractClientIp(request);
        String key = "ratelimit:" + endpoint.id() + ":" + clientIp;

        RateLimitDecision decision = rateLimiter.tryConsume(key, endpoint.toPolicy());

        if (decision.allowed()) {
            // 허용 — 안내 헤더 첨부 후 통과. 클라이언트가 한도 가까워졌음을 미리 감지 가능
            response.setHeader(HEADER_RATE_LIMIT_LIMIT, String.valueOf(endpoint.limit()));
            response.setHeader(HEADER_RATE_LIMIT_REMAINING, String.valueOf(decision.remaining()));
            chain.doFilter(request, response);
            return;
        }

        // 거부 — AUTH_014 + Retry-After + 한도 헤더
        // PII 노출 방지: 키 값 자체는 로그 미포함 (endpoint id만 운영 식별용)
        log.warn("Rate limit exceeded. endpoint={}", endpoint.id());
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(HEADER_RETRY_AFTER, String.valueOf(Math.max(1, decision.retryAfter().toSeconds())));
        headers.put(HEADER_RATE_LIMIT_LIMIT, String.valueOf(endpoint.limit()));
        headers.put(HEADER_RATE_LIMIT_REMAINING, "0");
        responseWriter.write(response, ErrorCode.RATE_LIMITED, headers);
    }

    /**
     * 요청 method + URI를 yml 정책과 매칭.
     *
     * <p>정확한 URI 매칭만 지원. 패턴 매칭 필요 시 AntPathMatcher 도입 (후속).
     */
    private Optional<EndpointPolicy> matchPolicy(HttpServletRequest request) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        return properties.endpoints().stream()
                .filter(policy -> policy.method().equalsIgnoreCase(method))
                .filter(policy -> policy.path().equals(uri))
                .findFirst();
    }

    /**
     * 클라이언트 IP 추출.
     *
     * <p>현재는 {@code getRemoteAddr()}만 사용. 운영 LB 뒤 배포 시 X-Forwarded-For 신뢰 정책은 Epic B S5
     * (SameSite/도메인 정책 확정)에서 결정. 그 전까지는 LB IP가 보호 대상이라 정책 효과 제한적이지만, 로컬/단일
     * 호스트 환경에서는 정상 동작.
     *
     * <p>followup: Spring {@code ForwardedHeaderFilter} + Trusted-Proxy 설정 도입.
     */
    private String extractClientIp(HttpServletRequest request) {
        return request.getRemoteAddr().toLowerCase(Locale.ROOT);
    }
}
