package com.chunbaetour.domain.auth;

import com.chunbaetour.domain.auth.dto.ReissueResponse;
import com.chunbaetour.domain.auth.jwt.TokenPair;
import com.chunbaetour.domain.auth.security.CookieProperties;
import com.chunbaetour.domain.auth.security.RefreshCookieFactory;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.ApiResponse;
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
 *   <li>{@code POST /api/v1/auth/logout} — S4에서 추가 예정 (블랙리스트 + Refresh 키 삭제)</li>
 * </ul>
 *
 * <p>경로가 {@code /api/v1/users/auth/**} 가 아니라 {@code /api/v1/auth/**}인 이유: 모든 role
 * (user/merchant/admin) 공통 endpoint이므로 page 분리하지 않는다. SecurityConfig의 permitAll 패턴에
 * 반드시 포함되어야 한다 (인증 필터가 본 endpoint를 보호 URL로 잘못 인식하면 reissue 자체가 불가).
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthTokenController {

    private final ReissueService reissueService;
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
    @PostMapping("/reissue")
    public ResponseEntity<ApiResponse<ReissueResponse>> reissue(HttpServletRequest request) {
        String refreshToken = extractRefreshToken(request);
        // Cookie 누락 또는 빈 값은 reissue 시도 자체가 비정상 → AUTH_005
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        TokenPair pair = reissueService.reissue(refreshToken);

        // 새 Refresh를 Cookie로 덮어쓴다 (Rotation: 이전 Cookie는 자연스럽게 교체)
        ResponseCookie newRefreshCookie = refreshCookieFactory.create(pair.refreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, newRefreshCookie.toString())
                .body(ApiResponse.success(ReissueResponse.from(pair)));
    }

    private String extractRefreshToken(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, cookieProperties.name());
        return cookie == null ? null : cookie.getValue();
    }
}
