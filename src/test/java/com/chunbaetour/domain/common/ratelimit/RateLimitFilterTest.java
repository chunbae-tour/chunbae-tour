package com.chunbaetour.domain.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.auth.security.SecurityResponseWriter;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.ratelimit.RateLimitProperties.EndpointPolicy;
import jakarta.servlet.FilterChain;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * {@link RateLimitFilter} 단위 테스트.
 *
 * <p>외부 의존(RateLimiter, SecurityResponseWriter, RateLimitProperties)을 모두 mock해서 필터 분기만 검증.
 *
 * <p>핵심 검증:
 * <ul>
 *   <li>enabled=false → 무조건 통과</li>
 *   <li>OPTIONS 요청 → CORS Preflight 우회</li>
 *   <li>매칭되는 정책 없음 → 통과</li>
 *   <li>허용 시 X-RateLimit-* 헤더 첨부 + 통과</li>
 *   <li>거부 시 AUTH_014 응답 + Retry-After 헤더 + 체인 중단</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private RateLimitProperties properties;

    @Mock
    private RateLimiter rateLimiter;

    @Mock
    private SecurityResponseWriter responseWriter;

    @Mock
    private FilterChain chain;

    @InjectMocks
    private RateLimitFilter filter;

    @Test
    void disabled_properties_skips_rate_limit_entirely() throws Exception {
        given(properties.enabled()).willReturn(false);

        MockHttpServletRequest request = post("/api/v1/users/auth/signup");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(rateLimiter, never()).tryConsume(any(), any());
        verify(responseWriter, never()).write(any(), any(), any());
    }

    @Test
    void options_preflight_skips_rate_limit() throws Exception {
        // CORS Preflight는 매칭되는 정책이 있어도 통과해야 함
        given(properties.enabled()).willReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/users/auth/signup");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(rateLimiter, never()).tryConsume(any(), any());
    }

    @Test
    void unmatched_endpoint_passes_through() throws Exception {
        given(properties.enabled()).willReturn(true);
        given(properties.endpoints()).willReturn(List.of(
                new EndpointPolicy("signup", "POST", "/api/v1/users/auth/signup", 3, Duration.ofMinutes(10))));

        // /api/v1/users/me는 정책 미매칭 → 통과
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(rateLimiter, never()).tryConsume(any(), any());
    }

    @Test
    void allowed_decision_attaches_rate_limit_headers_and_passes() throws Exception {
        given(properties.enabled()).willReturn(true);
        given(properties.endpoints()).willReturn(List.of(
                new EndpointPolicy("signup", "POST", "/api/v1/users/auth/signup", 3, Duration.ofMinutes(10))));
        given(rateLimiter.tryConsume(eq("ratelimit:signup:127.0.0.1"), any()))
                .willReturn(RateLimitDecision.allowed(2));

        MockHttpServletRequest request = post("/api/v1/users/auth/signup");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("3");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("2");
        assertThat(response.getHeader("Retry-After")).isNull();
    }

    @Test
    void denied_decision_writes_AUTH_014_with_retry_after_and_stops_chain() throws Exception {
        given(properties.enabled()).willReturn(true);
        given(properties.endpoints()).willReturn(List.of(
                new EndpointPolicy("user-login", "POST", "/api/v1/users/auth/login", 5, Duration.ofMinutes(1))));
        given(rateLimiter.tryConsume(eq("ratelimit:user-login:127.0.0.1"), any()))
                .willReturn(RateLimitDecision.denied(Duration.ofSeconds(45)));

        MockHttpServletRequest request = post("/api/v1/users/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        verify(responseWriter).write(eq(response), eq(ErrorCode.RATE_LIMITED), any());
    }

    @Test
    void method_mismatch_does_not_apply_policy() throws Exception {
        // 정책은 POST signup만 등록. GET으로 같은 경로 호출은 매칭 안 됨
        given(properties.enabled()).willReturn(true);
        given(properties.endpoints()).willReturn(List.of(
                new EndpointPolicy("signup", "POST", "/api/v1/users/auth/signup", 3, Duration.ofMinutes(10))));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/auth/signup");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(rateLimiter, never()).tryConsume(any(), any());
    }

    private static MockHttpServletRequest post(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr("127.0.0.1");
        return request;
    }
}
