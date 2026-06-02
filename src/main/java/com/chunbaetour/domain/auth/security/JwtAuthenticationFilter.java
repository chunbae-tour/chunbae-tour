package com.chunbaetour.domain.auth.security;

import com.chunbaetour.domain.auth.jwt.AccessClaims;
import com.chunbaetour.domain.auth.jwt.AccessTokenBlacklist;
import com.chunbaetour.domain.auth.jwt.TokenIssuer;
import com.chunbaetour.domain.common.audit.SecurityAuditEventType;
import com.chunbaetour.domain.common.audit.SecurityAuditLogger;
import com.chunbaetour.domain.common.error.ErrorCode;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bearer Access Token을 검증하고 SecurityContext에 인증 정보를 채우는 필터.
 *
 * <p>S4 추가 책임:
 * <ul>
 *   <li>verify 성공 후 {@link AccessTokenBlacklist#contains}로 로그아웃된 토큰인지 확인 → 등록 시 AUTH_013 응답</li>
 *   <li>logout endpoint에서 {@link AccessClaims}를 다시 파싱할 필요 없도록 request attribute에 저장
 *       ({@link #REQUEST_ATTR_ACCESS_CLAIMS})</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /**
     * Controller에서 검증된 AccessClaims를 꺼낼 때 사용할 request attribute 키.
     *
     * <p>예: {@code AuthTokenController.logout}이 {@code (AccessClaims) request.getAttribute(REQUEST_ATTR_ACCESS_CLAIMS)}로
     * tokenId/expiresAt/userId를 가져온다. JWT를 두 번 파싱하지 않기 위해.
     */
    public static final String REQUEST_ATTR_ACCESS_CLAIMS = "com.chunbaetour.auth.accessClaims";

    /**
     * 인증 검증을 건너뛸 공개 경로 패턴.
     *
     * <p>{@link com.chunbaetour.domain.auth.config.SecurityConfig}의 permitAll URL과 정확히 일치해야 한다.
     * 여기에 빠진 경로는 잘못된 Bearer 토큰이 와도 doFilterInternal에서 401 응답이 나가 permitAll endpoint
     * 호출이 차단된다.
     *
     * <p><b>S4 변경</b>: {@code /api/v1/auth/logout}은 인증 필요 경로이므로 본 리스트에서 명시적으로 제외하고
     * {@link #LOGOUT_PATH}와 비교하여 분기 처리한다 (Ant 패턴은 음수 매칭이 없어 직접 처리).
     */
    private static final List<String> PUBLIC_PATH_PATTERNS = List.of(
            "/api/v1/users/auth/**",
            // S5: 상인/관리자 로그인도 인증 없이 접근 가능
            "/api/v1/merchants/auth/**",
            "/api/v1/admin/auth/**",
            "/api/v1/auth/**",
            "/actuator/**",
            // WebSocket 핸드셰이크 + SockJS — STOMP 레벨에서 JWT 인증 처리하므로 HTTP 필터 스킵
            "/ws-stomp/**"
    );

    /**
     * GET 메서드 한정 공개 경로 패턴.
     *
     * <p>{@link com.chunbaetour.domain.auth.config.SecurityConfig}에서 특정 HTTP 메서드만 permitAll인 경로.
     * 경로만으로 skip하면 다른 메서드 요청 시 유효 토큰도 SecurityContext에 채워지지 않아 인증 실패.
     * GET /api/v1/shops/* — QR 스캔 후 결제창 진입 시 가게명·메뉴 fetch 용도. 만료 토큰 보유 유저도 차단되지 않아야 함.
     */
    private static final List<String> PUBLIC_GET_PATH_PATTERNS = List.of(
            "/api/v1/shops/*",
            // 관광지 조회/추천 API — 비로그인 허용 (isLiked는 서비스 단에서 userId null 체크)
            "/api/v1/places/**",
            "/api/v1/recommend/**",
            // 검색 조회 API — 인기/장소/축제/자동완성
            "/api/v1/search/popular",
            "/api/v1/search/places",
            "/api/v1/search/festivals",
            "/api/v1/search/suggest",
            // 커뮤니티 목록·단건 조회 — 비로그인 허용
            "/api/v1/community/posts/companions/**",
            "/api/v1/community/posts/free/**",
            // 스토어 상품 조회 — 비인증 공개 API
            "/api/v1/store/products/**",
            // 축제·캘린더 조회 — 비인증 허용
            "/api/v1/festivals/**",
            "/api/v1/calendar/**"
    );

    /**
     * POST 메서드 한정 공개 경로 패턴.
     *
     * <p>SecurityConfig에서 특정 POST 메서드만 permitAll인 경로.
     * PortOne 웹훅은 서버→서버 호출이라 Bearer 토큰이 없어 필터에서도 반드시 스킵해야 한다.
     */
    private static final List<String> PUBLIC_POST_PATH_PATTERNS = List.of(
            // PortOne 결제 웹훅 — 서버→서버 호출, JWT 없음
            "/api/v1/payments/webhook"
    );

    /** logout은 인증 필요. {@link #shouldNotFilter}에서 명시적으로 예외 처리. */
    private static final String LOGOUT_PATH = "/api/v1/auth/logout";

    /**
     * KAN-104 운영 모니터링 메트릭 이름.
     *
     * <p>Prometheus 노출 시 dot이 underscore로 변환됨 (예: {@code auth_jwt_verify_duration_seconds}).
     * 카탈로그({@code docs/operations/metrics-catalog.md})와 동기 유지.
     */
    private static final String METRIC_VERIFY_DURATION = "auth.jwt.verify.duration";
    private static final String METRIC_VERIFY_FAILURE = "auth.jwt.failure.total";

    private final TokenIssuer tokenIssuer;
    private final SecurityResponseWriter responseWriter;
    private final AccessTokenBlacklist accessTokenBlacklist;
    private final MeterRegistry meterRegistry;
    private final SecurityAuditLogger auditLogger;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        // logout은 인증 필요 경로 — public 매칭에서 명시적으로 제외하고 doFilterInternal로 진입시킨다.
        if (LOGOUT_PATH.equals(path)) {
            return false;
        }
        if ("GET".equals(request.getMethod())
                && PUBLIC_GET_PATH_PATTERNS.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path))) {
            return true;
        }
        if ("POST".equals(request.getMethod())
                && PUBLIC_POST_PATH_PATTERNS.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path))) {
            return true;
        }
        return PUBLIC_PATH_PATTERNS.stream()
                .anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = extractToken(request);
        if (token == null) {
            // Bearer 헤더가 없으면 다음 필터로 통과. 보호 URL이면 EntryPoint가 AUTH_006 응답.
            filterChain.doFilter(request, response);
            return;
        }

        // KAN-104: 메트릭 시작. outcome 분기별로 할당하고 finally에서 단일 stop()으로 기록.
        // 직접 stop을 분기마다 호출하는 패턴은 예상 밖 RuntimeException에서 Timer.Sample 누수 위험이 있어 회피.
        // 기본값 "unexpected"는 미커버 예외 발생 시 운영 알람 트리거용 sentinel.
        Timer.Sample verifySample = Timer.start(meterRegistry);
        String outcome = "unexpected";

        try {
            AccessClaims claims;
            try {
                claims = tokenIssuer.verifyAccess(token);
            } catch (ExpiredJwtException e) {
                outcome = "expired";
                meterRegistry.counter(METRIC_VERIFY_FAILURE, "code", "AUTH_002").increment();
                // KAN-105: 정상적 만료도 감사 로그 — brute force와 구분 + reissue 흐름 확인 가능
                auditLogger.emitFailure(SecurityAuditEventType.TOKEN_EXPIRED, null,
                        ErrorCode.ACCESS_TOKEN_EXPIRED.getCode(), Map.of());
                // exp 클레임 초과: 클라이언트가 reissue로 재발급해야 함
                responseWriter.write(response, ErrorCode.ACCESS_TOKEN_EXPIRED);
                return;
            } catch (JwtException | IllegalArgumentException e) {
                outcome = "tampered";
                meterRegistry.counter(METRIC_VERIFY_FAILURE, "code", "AUTH_003").increment();
                // KAN-105: 변조 시도 — 즉시 감사 로그. SIEM 알람 룰 트리거 대상
                auditLogger.emitFailure(SecurityAuditEventType.TOKEN_TAMPERED, null,
                        ErrorCode.ACCESS_TOKEN_INVALID.getCode(), Map.of());
                // 서명 오류/Malformed/잘못된 claim → 변조 가능성 (사유 노출 최소화)
                responseWriter.write(response, ErrorCode.ACCESS_TOKEN_INVALID);
                return;
            }

            boolean blacklisted;
            try {
                blacklisted = accessTokenBlacklist.contains(claims.tokenId());
            } catch (DataAccessException e) {
                outcome = "redis_failure";
                log.warn("Failed to check access token blacklist. tokenId={}", claims.tokenId(), e);
                responseWriter.write(response, ErrorCode.INTERNAL_SERVER_ERROR);
                return;
            }

            // S4: 로그아웃된 토큰은 verify 통과해도 거부. 블랙리스트 키는 토큰 만료 시점까지만 유지됨 → Redis 부하 제한적.
            if (blacklisted) {
                outcome = "blacklisted";
                meterRegistry.counter(METRIC_VERIFY_FAILURE, "code", "AUTH_013").increment();
                // KAN-105: 블랙리스트 재사용 = 탈취 의심 신호. actorId는 claims에서 추출 가능
                auditLogger.emitFailure(SecurityAuditEventType.TOKEN_BLACKLISTED, claims.userId(),
                        ErrorCode.BLACKLISTED_TOKEN.getCode(), Map.of());
                responseWriter.write(response, ErrorCode.BLACKLISTED_TOKEN);
                return;
            }

            outcome = "success";

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    claims.userId(),
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + claims.role().name()))
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // S4: Controller(logout 등)에서 JWT 재파싱 없이 tokenId/expiresAt에 접근할 수 있도록 attribute로 노출.
            request.setAttribute(REQUEST_ATTR_ACCESS_CLAIMS, claims);

            filterChain.doFilter(request, response);
        } finally {
            verifySample.stop(meterRegistry.timer(METRIC_VERIFY_DURATION, "outcome", outcome));
        }
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
