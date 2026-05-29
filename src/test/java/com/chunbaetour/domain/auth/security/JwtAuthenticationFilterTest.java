package com.chunbaetour.domain.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.auth.Role;
import com.chunbaetour.domain.auth.jwt.AccessClaims;
import com.chunbaetour.domain.auth.jwt.AccessTokenBlacklist;
import com.chunbaetour.domain.auth.jwt.TokenIssuer;
import com.chunbaetour.domain.common.audit.SecurityAuditLogger;
import com.chunbaetour.domain.common.error.ErrorCode;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * {@link JwtAuthenticationFilter} 단위 테스트.
 *
 * <p>S4 보강:
 * <ul>
 *   <li>블랙리스트 등록된 토큰 → AUTH_013, SecurityContext 미설정, 체인 중단</li>
 *   <li>정상 토큰 → request attribute에 AccessClaims 저장</li>
 *   <li>{@code /api/v1/auth/logout}은 PUBLIC 매칭에서 제외되어 filter 진입</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final Instant FUTURE_EXP = Instant.parse("2099-01-01T00:00:00Z");

    @Mock
    private TokenIssuer tokenIssuer;

    @Mock
    private SecurityResponseWriter responseWriter;

    @Mock
    private AccessTokenBlacklist accessTokenBlacklist;

    @Mock
    private FilterChain filterChain;

    /** KAN-104 메트릭 in-memory registry. */
    @Spy
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    /** KAN-105 감사 로그 mock. */
    @Mock
    private SecurityAuditLogger auditLogger;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_with_no_authorization_header_passes_through() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(responseWriter, never()).write(any(HttpServletResponse.class), any(ErrorCode.class));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        // sample 시작 전 return하므로 어떤 outcome도 카운트되면 안 됨 + unexpected sentinel 가드
        assertThat(meterRegistry.timer("auth.jwt.verify.duration", "outcome", "success").count())
                .isEqualTo(0L);
        assertThat(meterRegistry.timer("auth.jwt.verify.duration", "outcome", "unexpected").count())
                .isEqualTo(0L);
    }

    @Test
    void doFilterInternal_with_valid_token_sets_authentication_and_attribute_and_passes_through() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AccessClaims claims = new AccessClaims(42L, Role.USER, "user@example.com", "tid", FUTURE_EXP);
        given(tokenIssuer.verifyAccess("valid-token")).willReturn(claims);
        given(accessTokenBlacklist.contains("tid")).willReturn(false);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(42L);
        assertThat(authentication.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");

        // S4: Controller에서 재파싱 없이 claims를 가져올 수 있도록 attribute로 노출되어야 함
        assertThat(request.getAttribute(JwtAuthenticationFilter.REQUEST_ATTR_ACCESS_CLAIMS)).isSameAs(claims);

        // KAN-104: 정상 분기는 outcome=success 한 번만 기록. unexpected sentinel은 0이어야 함 (단일 stop 회귀 가드)
        assertThat(meterRegistry.timer("auth.jwt.verify.duration", "outcome", "success").count())
                .isEqualTo(1L);
        assertThat(meterRegistry.timer("auth.jwt.verify.duration", "outcome", "unexpected").count())
                .isEqualTo(0L);
    }

    @Test
    void doFilter_with_public_path_skips_token_validation() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/v1/users/auth/login");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer expired");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(tokenIssuer, never()).verifyAccess("expired");
        verify(responseWriter, never()).write(any(HttpServletResponse.class), any(ErrorCode.class));
        verify(filterChain).doFilter(request, response);

        // public path → doFilterInternal 미진입. 어떤 outcome도 카운트 0.
        assertThat(meterRegistry.timer("auth.jwt.verify.duration", "outcome", "success").count())
                .isEqualTo(0L);
        assertThat(meterRegistry.timer("auth.jwt.verify.duration", "outcome", "unexpected").count())
                .isEqualTo(0L);
    }

    @Test
    void doFilter_with_logout_path_does_NOT_skip_validation() throws Exception {
        // S4: /api/v1/auth/logout은 인증 필요. PUBLIC 매칭에서 명시적으로 제외되어 doFilterInternal 진입.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/v1/auth/logout");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AccessClaims claims = new AccessClaims(1L, Role.USER, "u@e.c", "tid", FUTURE_EXP);
        given(tokenIssuer.verifyAccess("valid-token")).willReturn(claims);
        given(accessTokenBlacklist.contains("tid")).willReturn(false);

        filter.doFilter(request, response, filterChain);

        // verify가 호출되었어야 한다 (public 매칭으로 우회되지 않음)
        verify(tokenIssuer).verifyAccess("valid-token");
        verify(filterChain).doFilter(request, response);

        // logout 경로도 정상 토큰 → success 분기로 통과. unexpected sentinel 가드.
        assertThat(meterRegistry.timer("auth.jwt.verify.duration", "outcome", "success").count())
                .isEqualTo(1L);
        assertThat(meterRegistry.timer("auth.jwt.verify.duration", "outcome", "unexpected").count())
                .isEqualTo(0L);
    }

    @Test
    void doFilterInternal_with_expired_token_writes_AUTH_002_and_stops_chain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer expired");
        MockHttpServletResponse response = new MockHttpServletResponse();
        willThrow(new ExpiredJwtException(null, null, "expired"))
                .given(tokenIssuer).verifyAccess("expired");

        filter.doFilter(request, response, filterChain);

        verify(responseWriter).write(response, ErrorCode.ACCESS_TOKEN_EXPIRED);
        verify(filterChain, never()).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        // KAN-104: expired 분기 Timer + AUTH_002 Counter 각 1회. unexpected 절대 0.
        assertThat(meterRegistry.timer("auth.jwt.verify.duration", "outcome", "expired").count())
                .isEqualTo(1L);
        assertThat(meterRegistry.counter("auth.jwt.failure.total", "code", "AUTH_002").count())
                .isEqualTo(1.0);
        assertThat(meterRegistry.timer("auth.jwt.verify.duration", "outcome", "unexpected").count())
                .isEqualTo(0L);
    }

    @Test
    void doFilterInternal_with_invalid_token_writes_AUTH_003_and_stops_chain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer tampered");
        MockHttpServletResponse response = new MockHttpServletResponse();
        willThrow(new MalformedJwtException("bad"))
                .given(tokenIssuer).verifyAccess("tampered");

        filter.doFilter(request, response, filterChain);

        verify(responseWriter).write(response, ErrorCode.ACCESS_TOKEN_INVALID);
        verify(filterChain, never()).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        // KAN-104: tampered 분기 Timer + AUTH_003 Counter 각 1회. unexpected 절대 0.
        assertThat(meterRegistry.timer("auth.jwt.verify.duration", "outcome", "tampered").count())
                .isEqualTo(1L);
        assertThat(meterRegistry.counter("auth.jwt.failure.total", "code", "AUTH_003").count())
                .isEqualTo(1.0);
        assertThat(meterRegistry.timer("auth.jwt.verify.duration", "outcome", "unexpected").count())
                .isEqualTo(0L);
    }

    @Test
    void doFilterInternal_with_blacklisted_token_writes_AUTH_013_and_stops_chain() throws Exception {
        // S4: 로그아웃 후 블랙리스트 등록된 토큰으로 재요청 시 거부.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer logged-out-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AccessClaims claims = new AccessClaims(1L, Role.USER, "u@e.c", "blacklisted-tid", FUTURE_EXP);
        given(tokenIssuer.verifyAccess("logged-out-token")).willReturn(claims);
        given(accessTokenBlacklist.contains("blacklisted-tid")).willReturn(true);

        filter.doFilter(request, response, filterChain);

        verify(responseWriter).write(response, ErrorCode.BLACKLISTED_TOKEN);
        verify(filterChain, never()).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        // attribute도 안 채워져야 한다 (controller가 logout 흐름 외에 claims 사용 못 함)
        assertThat(request.getAttribute(JwtAuthenticationFilter.REQUEST_ATTR_ACCESS_CLAIMS)).isNull();

        // KAN-104: blacklisted 분기 Timer + AUTH_013 Counter 각 1회. unexpected 절대 0.
        assertThat(meterRegistry.timer("auth.jwt.verify.duration", "outcome", "blacklisted").count())
                .isEqualTo(1L);
        assertThat(meterRegistry.counter("auth.jwt.failure.total", "code", "AUTH_013").count())
                .isEqualTo(1.0);
        assertThat(meterRegistry.timer("auth.jwt.verify.duration", "outcome", "unexpected").count())
                .isEqualTo(0L);
    }

    @Test
    void doFilterInternal_when_blacklist_lookup_fails_writes_COMMON_001_and_stops_chain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AccessClaims claims = new AccessClaims(1L, Role.USER, "u@e.c", "tid", FUTURE_EXP);
        given(tokenIssuer.verifyAccess("valid-token")).willReturn(claims);
        willThrow(new DataAccessResourceFailureException("redis unavailable"))
                .given(accessTokenBlacklist).contains("tid");

        filter.doFilter(request, response, filterChain);

        verify(responseWriter).write(response, ErrorCode.INTERNAL_SERVER_ERROR);
        verify(filterChain, never()).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.REQUEST_ATTR_ACCESS_CLAIMS)).isNull();

        // KAN-104: Redis 조회 실패 분기 Timer 1회. unexpected 절대 0 (단일 stop 가드).
        assertThat(meterRegistry.timer("auth.jwt.verify.duration", "outcome", "redis_failure").count())
                .isEqualTo(1L);
        assertThat(meterRegistry.timer("auth.jwt.verify.duration", "outcome", "unexpected").count())
                .isEqualTo(0L);
    }

    @Test
    void doFilterInternal_with_bearer_header_having_empty_token_passes_through() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(responseWriter, never()).write(any(HttpServletResponse.class), any(ErrorCode.class));

        // Bearer 다음 토큰 비어있어 sample 시작 전 return. 어떤 outcome도 카운트 0.
        assertThat(meterRegistry.timer("auth.jwt.verify.duration", "outcome", "success").count())
                .isEqualTo(0L);
        assertThat(meterRegistry.timer("auth.jwt.verify.duration", "outcome", "unexpected").count())
                .isEqualTo(0L);
    }
}
