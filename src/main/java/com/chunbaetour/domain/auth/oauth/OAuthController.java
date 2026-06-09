package com.chunbaetour.domain.auth.oauth;

import com.chunbaetour.domain.auth.OauthProvider;
import com.chunbaetour.domain.auth.dto.OauthLoginRequest;
import com.chunbaetour.domain.auth.dto.OauthLoginResponse;
import com.chunbaetour.domain.auth.dto.OauthSignupRequest;
import com.chunbaetour.domain.auth.jwt.TokenPair;
import com.chunbaetour.domain.auth.security.RefreshCookieFactory;
import com.chunbaetour.domain.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 소셜 로그인(카카오/네이버) 인증 endpoint.
 *
 * <ul>
 *   <li>{@code POST /api/v1/users/auth/oauth/{kakao|naver}} — 인가코드로 로그인. 기존 계정이면 토큰 발급,
 *       신규면 {@code needSignup=true} + 가입 티켓 반환.</li>
 *   <li>{@code POST /api/v1/users/auth/oauth/signup} — 가입 티켓 + 추가정보로 계정 생성 + 토큰 발급.</li>
 * </ul>
 *
 * <p>로그인 흐름과 동일하게 access는 Body, refresh는 HttpOnly 쿠키로 내려준다(USER role 고정).
 */
@Tag(name = "USER 소셜 로그인", description = "카카오·네이버 로그인 (POST /api/v1/users/auth/oauth/**)")
@RestController
@RequestMapping("/api/v1/users/auth/oauth")
@RequiredArgsConstructor
public class OAuthController {

    private final OauthLoginService oauthLoginService;
    private final OauthSignupService oauthSignupService;
    private final RefreshCookieFactory refreshCookieFactory;

    @SecurityRequirements
    @Operation(
            summary = "소셜 로그인 (1단계)",
            description = "인가코드로 로그인. 신규면 needSignup=true + 가입 티켓 반환.\n\n"
                    + "[보안 전제 — CSRF/state] 본 엔드포인트는 SPA가 OAuth state(또는 PKCE) 검증을 "
                    + "완료한 뒤에만 호출하는 것을 전제로 한다. 프론트가 인가 요청 시 state를 생성·보관하고 "
                    + "콜백에서 일치할 때만 code를 본 API로 전달해야 한다(login CSRF 방어). 백엔드는 미인증 "
                    + "로그인 진입점이라 바인딩할 세션이 없어 서버측 state를 강제하지 않으며, 추가 방어로 IP "
                    + "rate-limit을 적용한다. 서버 발급 state/PKCE(백엔드 검증)는 hardening follow-up.")
    @PostMapping("/{provider}")
    public ResponseEntity<ApiResponse<OauthLoginResponse>> oauthLogin(
            @PathVariable String provider,
            @Valid @RequestBody OauthLoginRequest request
    ) {
        OauthProvider oauthProvider = OauthProvider.fromPath(provider);
        OauthLoginResult result = oauthLoginService.login(oauthProvider, request.code(), request.redirectUri());

        if (result.needSignup()) {
            // 신규 — 토큰/쿠키 없이 가입 티켓 + prefill만 반환.
            return ResponseEntity.ok(ApiResponse.success(
                    OauthLoginResponse.needSignup(result.signupTicket(), result.email(), result.nickname())));
        }

        TokenPair pair = result.tokenPair();
        ResponseCookie refreshCookie = refreshCookieFactory.create(pair.refreshToken());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponse.success(OauthLoginResponse.loggedIn(pair.accessToken(), pair.role())));
    }

    @SecurityRequirements
    @Operation(summary = "소셜 가입 (2단계)",
            description = "가입 티켓 + 추가정보(이름/전화/생년월일/닉네임)로 계정 생성. "
                    + "이메일은 요청에 받지 않고 1단계 티켓에 서명돼 온 공급자 검증 이메일을 사용한다(선점 방지).")
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<OauthLoginResponse>> oauthSignup(
            @Valid @RequestBody OauthSignupRequest request
    ) {
        TokenPair pair = oauthSignupService.signup(request);
        ResponseCookie refreshCookie = refreshCookieFactory.create(pair.refreshToken());
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponse.success(OauthLoginResponse.loggedIn(pair.accessToken(), pair.role())));
    }
}
