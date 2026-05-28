package com.chunbaetour.domain.auth;

import com.chunbaetour.domain.auth.dto.LoginRequest;
import com.chunbaetour.domain.auth.dto.LoginResponse;
import com.chunbaetour.domain.auth.jwt.TokenPair;
import com.chunbaetour.domain.auth.security.RefreshCookieFactory;
import com.chunbaetour.domain.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * 상인(MERCHANT) 페이지 인증 endpoint.
 *
 * <ul>
 *   <li>{@code POST /api/v1/merchants/auth/login} — 상인 전용 로그인</li>
 * </ul>
 *
 * <p>설계 결정 — 로그인 endpoint를 페이지별로 분리:
 * <ul>
 *   <li>{@link UserAuthController}에서 USER, 본 컨트롤러에서 MERCHANT, {@link AdminAuthController}에서 ADMIN을 처리한다.</li>
 *   <li>role 검증은 controller가 아닌 {@link LoginService#login}의 {@code requiredRole} 인자로 위임 — 도메인 서비스가 단일 진실 원천이며,
 *       각 controller는 자기 page의 role을 명시적으로 전달한다.</li>
 *   <li>USER 계정이 본 endpoint로 로그인 시도하면 LoginService가 {@code AUTH_007}을 던진다.</li>
 * </ul>
 *
 * <p>회원가입은 USER 전용 흐름이므로 본 컨트롤러에는 signup이 없다. 상인 계정은 별도 "상인 신청/승인" 흐름(별도 PRD)에서 생성된다.
 *
 * <p>reissue/logout은 {@link AuthTokenController}의 {@code /api/v1/auth/**} 공통 endpoint를 사용 — 페이지별로 분리하지 않는다.
 */
@Tag(name = "MERCHANT 인증", description = "상인 로그인 (POST /api/v1/merchants/auth/**)")
@RestController
@RequestMapping("/api/v1/merchants/auth")
@RequiredArgsConstructor
public class MerchantAuthController {

    private final LoginService loginService;
    private final RefreshCookieFactory refreshCookieFactory;

    /**
     * 상인 로그인.
     *
     * <p>{@link UserAuthController#login}과 동일한 응답 형태 (Body=accessToken+role, Cookie=refreshToken).
     * 차이점: {@code requiredRole=MERCHANT} 고정 전달 → USER/ADMIN 계정은 AUTH_007.
     */
    @Operation(summary = "상인 로그인")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        TokenPair pair = loginService.login(request.email(), request.password(), Role.MERCHANT);
        ResponseCookie refreshCookie = refreshCookieFactory.create(pair.refreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponse.success(LoginResponse.from(pair)));
    }
}
