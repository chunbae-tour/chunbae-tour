package com.chunbaetour.domain.auth.security;

import com.chunbaetour.domain.auth.jwt.AccessClaims;
import com.chunbaetour.domain.auth.jwt.AccessTokenBlacklist;
import com.chunbaetour.domain.auth.jwt.TokenIssuer;
import com.chunbaetour.domain.common.error.ErrorCode;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
            "/api/v1/auth/**",
            "/actuator/**"
    );

    /** logout은 인증 필요. {@link #shouldNotFilter}에서 명시적으로 예외 처리. */
    private static final String LOGOUT_PATH = "/api/v1/auth/logout";

    private final TokenIssuer tokenIssuer;
    private final SecurityResponseWriter responseWriter;
    private final AccessTokenBlacklist accessTokenBlacklist;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        // logout은 인증 필요 경로 — public 매칭에서 명시적으로 제외하고 doFilterInternal로 진입시킨다.
        if (LOGOUT_PATH.equals(path)) {
            return false;
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

        AccessClaims claims;
        try {
            claims = tokenIssuer.verifyAccess(token);
        } catch (ExpiredJwtException e) {
            // exp 클레임 초과: 클라이언트가 reissue로 재발급해야 함
            responseWriter.write(response, ErrorCode.ACCESS_TOKEN_EXPIRED);
            return;
        } catch (JwtException | IllegalArgumentException e) {
            // 서명 오류/Malformed/잘못된 claim → 변조 가능성 (사유 노출 최소화)
            responseWriter.write(response, ErrorCode.ACCESS_TOKEN_INVALID);
            return;
        }

        // S4: 로그아웃된 토큰은 verify 통과해도 거부. 블랙리스트 키는 토큰 만료 시점까지만 유지됨 → Redis 부하 제한적.
        if (accessTokenBlacklist.contains(claims.tokenId())) {
            responseWriter.write(response, ErrorCode.BLACKLISTED_TOKEN);
            return;
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                claims.userId(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + claims.role().name()))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // S4: Controller(logout 등)에서 JWT 재파싱 없이 tokenId/expiresAt에 접근할 수 있도록 attribute로 노출.
        request.setAttribute(REQUEST_ATTR_ACCESS_CLAIMS, claims);

        filterChain.doFilter(request, response);
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
