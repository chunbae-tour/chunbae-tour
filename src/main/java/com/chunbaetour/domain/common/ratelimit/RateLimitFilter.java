package com.chunbaetour.domain.common.ratelimit;

import com.chunbaetour.domain.auth.security.SecurityResponseWriter;
import com.chunbaetour.domain.common.audit.SecurityAuditEventType;
import com.chunbaetour.domain.common.audit.SecurityAuditLogger;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.ratelimit.RateLimitProperties.EndpointPolicy;
import com.chunbaetour.domain.common.util.IpMaskUtil;
import io.micrometer.core.instrument.MeterRegistry;
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
import org.springframework.util.AntPathMatcher;
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

    /** KAN-104 메트릭 — endpoint별 allowed/denied 빈도. 카탈로그({@code docs/operations/metrics-catalog.md}) 동기. */
    private static final String METRIC_DECISION = "ratelimit.decision.total";

    private final RateLimitProperties properties;
    private final RateLimiter rateLimiter;
    private final SecurityResponseWriter responseWriter;
    private final MeterRegistry meterRegistry;
    private final SecurityAuditLogger auditLogger;

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

        // RateLimiter 구현체는 인프라 예외를 RateLimitDecision.denied로 흡수하는 contract (RateLimiter Javadoc 참조).
        // 본 필터에서 try/catch 추가 금지 — 변환되지 않은 예외(NPE 등)는 의도적으로 전파되어
        // GlobalExceptionHandler가 500 ApiResponse로 정규화한다.
        RateLimitDecision decision = rateLimiter.tryConsume(key, endpoint.toPolicy());

        if (decision.allowed()) {
            // 허용 — 안내 헤더 첨부 후 통과. 클라이언트가 한도 가까워졌음을 미리 감지 가능
            meterRegistry.counter(METRIC_DECISION, "endpoint", endpoint.id(), "decision", "allowed").increment();
            response.setHeader(HEADER_RATE_LIMIT_LIMIT, String.valueOf(endpoint.limit()));
            response.setHeader(HEADER_RATE_LIMIT_REMAINING, String.valueOf(decision.remaining()));
            chain.doFilter(request, response);
            return;
        }

        // 거부 — AUTH_014 + Retry-After + 한도 헤더
        // PII 노출 방지: 키 값 자체는 로그 미포함 (endpoint id만 운영 식별용)
        meterRegistry.counter(METRIC_DECISION, "endpoint", endpoint.id(), "decision", "denied").increment();
        log.warn("Rate limit exceeded. endpoint={}, ip={}", endpoint.id(), IpMaskUtil.mask(clientIp));
        // KAN-105: rate limit 거부도 감사 로그 — endpoint별 공격 패턴 추적. actorId 없음(인증 전).
        // SecurityAuditEvent.metadata는 민감 값 금지 계약(SecurityAuditEvent Javadoc) — clientIp도 IpMaskUtil.mask로
        // 마스킹해 적재한다. 이벤트 자동 필드 ipMasked(getRemoteAddr 마스킹)·warn 로그(라인 116)와 동일한 마스킹 정책 유지.
        // raw IP는 audit.security 채널(별도 JSON appender, 1년 보존·SIEM 수집) 어디에도 남기지 않는다.
        auditLogger.emitFailure(SecurityAuditEventType.RATE_LIMIT_DENIED, null,
                ErrorCode.RATE_LIMITED.getCode(),
                java.util.Map.of("endpoint", endpoint.id(), "clientIp", IpMaskUtil.mask(clientIp)));
        Map<String, String> headers = new LinkedHashMap<>();
        // Redis TTL race 등으로 retryAfter가 0초가 될 수 있어도 HTTP Retry-After 헤더는 최소 1초로 응답.
        // 0초 응답 시 클라이언트가 즉시 재시도해 무한 루프 위험.
        headers.put(HEADER_RETRY_AFTER, String.valueOf(Math.max(1, decision.retryAfter().toSeconds())));
        headers.put(HEADER_RATE_LIMIT_LIMIT, String.valueOf(endpoint.limit()));
        headers.put(HEADER_RATE_LIMIT_REMAINING, "0");
        responseWriter.write(response, ErrorCode.RATE_LIMITED, headers);
    }

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /**
     * 요청 method + URI를 yml 정책과 매칭.
     *
     * <p>AntPathMatcher로 와일드카드 패턴 지원 (예: /api/v1/payments/qr/&#42;/status).
     * 구체적 경로(concrete path)도 Ant 매칭에서 동일하게 동작하므로 기존 정책 호환.
     */
    private Optional<EndpointPolicy> matchPolicy(HttpServletRequest request) {
        String method = request.getMethod();
        // context-path 추가 시에도 yml의 path와 일관 매칭. getServletPath()는 MockMvc 기본 ""을 반환해
        // 테스트가 깨지므로 requestURI에서 contextPath만 절단하는 방식 채택 (contextPath 미설정 시 "" → 전체 URI 유지).
        String uri = request.getRequestURI().substring(request.getContextPath().length());
        return properties.endpoints().stream()
                .filter(policy -> policy.method().equalsIgnoreCase(method))
                .filter(policy -> PATH_MATCHER.match(policy.path(), uri))
                .findFirst();
    }

    /**
     * 클라이언트 IP 추출.
     *
     * <p>application.yml의 {@code server.forward-headers-strategy: NATIVE} 설정으로
     * Tomcat RemoteIpValve가 X-Forwarded-For 헤더를 처리해 {@code getRemoteAddr()}가 실제
     * 클라이언트 IP를 반환한다.
     *
     * <p>Internal proxy 신뢰 범위는 {@code server.tomcat.remoteip.internal-proxies} 정규식으로
     * 제어한다. Spring Boot 기본값은 RFC 1918 사설 IP 대역 regex. 운영 LB가 사설 대역 밖이면
     * 해당 LB IP를 매칭하는 regex를 명시 설정해야 한다.
     *
     * <p>Trusted proxy allowlist를 운영 환경에 맞게 조정하지 않으면 X-Forwarded-For spoofing
     * 위험이 있으므로, 운영 배포 전 별도 후속 작업으로 검증 필요.
     */
    private String extractClientIp(HttpServletRequest request) {
        return request.getRemoteAddr().toLowerCase(Locale.ROOT);
    }
}
