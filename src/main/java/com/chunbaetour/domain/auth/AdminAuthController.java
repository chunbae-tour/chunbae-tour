package com.chunbaetour.domain.auth;

import com.chunbaetour.domain.auth.dto.LoginRequest;
import com.chunbaetour.domain.auth.dto.LoginResponse;
import com.chunbaetour.domain.auth.jwt.TokenPair;
import com.chunbaetour.domain.auth.security.RefreshCookieFactory;
import com.chunbaetour.domain.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자(ADMIN) 페이지 인증 endpoint.
 *
 * <ul>
 *   <li>{@code POST /api/v1/admin/auth/login} — 관리자 전용 로그인</li>
 * </ul>
 *
 * <p>설계 결정: {@link MerchantAuthController} Javadoc 참조 — 페이지별 endpoint 분리 정책의 일환.
 *
 * <p>관리자 계정은 별도 운영 도구 또는 수동 시드로 생성된다 (회원가입 흐름 없음).
 *
 * <p>reissue/logout은 {@link AuthTokenController}의 {@code /api/v1/auth/**} 공통 endpoint를 사용.
 */
@RestController
@RequestMapping("/api/v1/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final LoginService loginService;
    private final RefreshCookieFactory refreshCookieFactory;

    /**
     * 관리자 로그인.
     *
     * <p>{@code requiredRole=ADMIN} 고정 전달 → USER/MERCHANT 계정은 AUTH_007.
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        TokenPair pair = loginService.login(request.email(), request.password(), Role.ADMIN);
        ResponseCookie refreshCookie = refreshCookieFactory.create(pair.refreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponse.success(LoginResponse.from(pair)));
    }
}
