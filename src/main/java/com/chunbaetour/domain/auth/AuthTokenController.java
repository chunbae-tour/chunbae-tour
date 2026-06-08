package com.chunbaetour.domain.auth;

import com.chunbaetour.domain.auth.dto.ReissueResponse;
import com.chunbaetour.domain.auth.jwt.AccessClaims;
import com.chunbaetour.domain.auth.jwt.TokenPair;
import com.chunbaetour.domain.auth.security.CookieProperties;
import com.chunbaetour.domain.auth.security.JwtAuthenticationFilter;
import com.chunbaetour.domain.auth.security.RefreshCookieFactory;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.WebUtils;

/**
 * USER/MERCHANT/ADMIN 공통 토큰 endpoint.
 *
 * <ul>
 *   <li>{@code POST /api/v1/auth/reissue} — Refresh Cookie로 새 Access + 새 Refresh 발급 (Rotation)</li>
 *   <li>{@code POST /api/v1/auth/logout} — Access 블랙리스트 등록 + Refresh 삭제 + 만료 Cookie 응답</li>
 * </ul>
 *
 * <p>경로가 {@code /api/v1/users/auth/**} 가 아니라 {@code /api/v1/auth/**}인 이유: 모든 role
 * (user/merchant/admin) 공통 endpoint이므로 page 분리하지 않는다. SecurityConfig URL 매핑:
 * <ul>
 *   <li>{@code /api/v1/auth/logout} → authenticated (인증 필수)</li>
 *   <li>나머지 {@code /api/v1/auth/**} → permitAll (인증 전에도 호출 가능)</li>
 * </ul>
 */
@Tag(name = "공통 토큰", description = "Access Token 재발급·로그아웃 (POST /api/v1/auth/**)")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthTokenController {

    private final ReissueService reissueService;
    private final LogoutService logoutService;
    private final RefreshCookieFactory refreshCookieFactory;
    private final CookieProperties cookieProperties;

    /**
     * Access Token 재발급.
     *
     * <p>입력: HttpOnly Cookie의 설정된 refresh cookie 이름 (브라우저가 자동 전송)
     * <br>출력: 새 Access (Body) + 새 Refresh Cookie (Set-Cookie 헤더)
     *
     * <p>쿠키 이름은 발급과 조회가 반드시 같은 설정을 써야 한다. {@code @CookieValue}는 런타임 설정값을
     * name에 넣을 수 없으므로 request에서 직접 꺼내고, 누락/빈 값은 AUTH_005로 통일한다.
     */
    @Operation(summary = "Access Token 재발급")
    @PostMapping("/reissue")
    public ResponseEntity<ApiResponse<ReissueResponse>> reissue(HttpServletRequest request) {
        String refreshToken = extractRefreshToken(request);
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        TokenPair pair = reissueService.reissue(refreshToken);
        ResponseCookie newRefreshCookie = refreshCookieFactory.create(pair.refreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, newRefreshCookie.toString())
                .body(ApiResponse.success(ReissueResponse.from(pair)));
    }

    /**
     * 로그아웃.
     *
     * <p>흐름:
     * <ol>
     *   <li>SecurityConfig가 본 endpoint를 인증 필수로 지정 → 호출 도달 시점에 JwtAuthenticationFilter가 이미
     *       Access Token을 verify하고 {@link AccessClaims}를 request attribute에 넣어둠.</li>
     *   <li>LogoutService에 위임 → 블랙리스트 등록 + Refresh 삭제.</li>
     *   <li>응답: 204 No Content + {@code Set-Cookie}로 Refresh Cookie 만료 처리.</li>
     * </ol>
     *
     * <p>request attribute가 비어 있는 경우는 정상 흐름에서 발생할 수 없지만 (필터가 SecurityContext와 attribute를
     * 같은 분기에서 채우므로), 방어 코드로 AUTH_006을 던진다.
     */
    @Operation(summary = "로그아웃")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        Object attr = request.getAttribute(JwtAuthenticationFilter.REQUEST_ATTR_ACCESS_CLAIMS);
        if (!(attr instanceof AccessClaims claims)) {
            // 인증 필터를 통과했는데 attribute가 없는 비정상 상태. 안전하게 거부.
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }

        logoutService.logout(claims);

        // 클라이언트의 Refresh Cookie를 즉시 만료 (Max-Age=0). 같은 name/path여야 브라우저가 덮어쓴다.
        ResponseCookie expiredCookie = refreshCookieFactory.expire();

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expiredCookie.toString())
                .build();
    }

    private String extractRefreshToken(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, cookieProperties.name());
        return cookie == null ? null : cookie.getValue();
    }
}
