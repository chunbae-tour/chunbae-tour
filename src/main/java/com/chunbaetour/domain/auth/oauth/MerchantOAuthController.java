package com.chunbaetour.domain.auth.oauth;

import com.chunbaetour.domain.auth.OauthProvider;
import com.chunbaetour.domain.auth.Role;
import com.chunbaetour.domain.auth.dto.OauthLoginRequest;
import com.chunbaetour.domain.auth.dto.OauthLoginResponse;
import com.chunbaetour.domain.auth.jwt.TokenPair;
import com.chunbaetour.domain.auth.security.RefreshCookieFactory;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 상인(MERCHANT) 소셜 로그인 endpoint.
 *
 * <ul>
 *   <li>{@code POST /api/v1/merchants/auth/oauth/{kakao|naver}} — 인가코드로 상인 소셜 로그인. MERCHANT 계정만 토큰 발급.</li>
 * </ul>
 *
 * <p>설계 결정 — 소셜 로그인도 이메일 로그인({@link com.chunbaetour.domain.auth.MerchantAuthController})처럼
 * 페이지별 role 진입점을 분리한다. {@link OAuthController}(USER)와 본 컨트롤러(MERCHANT)는 같은
 * {@link OauthLoginService#login}을 호출하되 {@code requiredRole}만 다르게 전달한다.
 *
 * <p>회원가입(signup)은 USER 전용 흐름이라 본 컨트롤러에 없다. 상인 계정은 상인 신청/승인 흐름에서 생성되며,
 * 미가입 소셜 신원이 본 endpoint로 들어오면 {@link OauthLoginService}가 {@code ACCESS_DENIED}로 거부한다
 * (needSignup 분기는 USER 진입점에서만 발생). 따라서 본 컨트롤러의 로그인 결과는 항상 토큰 발급이다.
 */
@Tag(name = "MERCHANT 소셜 로그인", description = "상인 카카오·네이버 로그인 (POST /api/v1/merchants/auth/oauth/**)")
@RestController
@RequestMapping("/api/v1/merchants/auth/oauth")
@RequiredArgsConstructor
public class MerchantOAuthController {

    private final OauthLoginService oauthLoginService;
    private final RefreshCookieFactory refreshCookieFactory;

    @SecurityRequirements
    @Operation(
            summary = "상인 소셜 로그인",
            description = "인가코드로 상인 소셜 로그인. MERCHANT 계정만 허용하며, USER/미가입 계정은 403(ACCESS_DENIED).\n\n"
                    + "[보안 전제 — CSRF/state] USER 소셜 로그인과 동일하게 프론트가 OAuth state(또는 PKCE) 검증을 "
                    + "완료한 뒤에만 호출하는 것을 전제로 한다.")
    @PostMapping("/{provider}")
    public ResponseEntity<ApiResponse<OauthLoginResponse>> oauthLogin(
            @PathVariable String provider,
            @Valid @RequestBody OauthLoginRequest request
    ) {
        OauthProvider oauthProvider = OauthProvider.fromPath(provider);
        // 상인 진입점 — 계정 role이 MERCHANT인 소셜 로그인만 허용. USER/미가입은 ACCESS_DENIED.
        OauthLoginResult result = oauthLoginService.login(
                oauthProvider, request.code(), request.redirectUri(), Role.MERCHANT);

        // 상인 진입점은 needSignup 분기가 발생하지 않는다(비-USER 미가입은 서비스에서 거부) — 항상 토큰 발급.
        // 방어가드: 향후 서비스 거동이 바뀌어 needSignup이 반환돼도 tokenPair() NPE 대신 명시적 거부.
        if (result.needSignup()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        TokenPair pair = result.tokenPair();
        ResponseCookie refreshCookie = refreshCookieFactory.create(pair.refreshToken());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponse.success(OauthLoginResponse.loggedIn(pair.accessToken(), pair.role())));
    }
}
