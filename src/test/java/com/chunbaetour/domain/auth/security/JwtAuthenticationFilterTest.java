package com.chunbaetour.domain.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.BDDMockito.willThrow;

import com.chunbaetour.domain.auth.Role;
import com.chunbaetour.domain.auth.jwt.AccessClaims;
import com.chunbaetour.domain.auth.jwt.TokenIssuer;
import com.chunbaetour.domain.common.error.ErrorCode;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private TokenIssuer tokenIssuer;

    @Mock
    private SecurityResponseWriter responseWriter;

    @Mock
    private FilterChain filterChain;

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
    }

    @Test
    void doFilterInternal_with_valid_token_sets_authentication_and_passes_through() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        given(tokenIssuer.verifyAccess("valid-token"))
                .willReturn(new AccessClaims(42L, Role.USER, "user@example.com", "tid"));

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(42L);
        assertThat(authentication.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");
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
    }

    @Test
    void doFilterInternal_with_bearer_header_having_empty_token_passes_through() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(responseWriter, never()).write(any(HttpServletResponse.class), any(ErrorCode.class));
    }
}
