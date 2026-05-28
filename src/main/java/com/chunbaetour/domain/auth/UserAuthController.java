package com.chunbaetour.domain.auth;

import com.chunbaetour.domain.auth.dto.LoginRequest;
import com.chunbaetour.domain.auth.dto.LoginResponse;
import com.chunbaetour.domain.auth.dto.SignupRequest;
import com.chunbaetour.domain.auth.dto.SignupResponse;
import com.chunbaetour.domain.auth.jwt.TokenPair;
import com.chunbaetour.domain.auth.security.RefreshCookieFactory;
import com.chunbaetour.domain.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 일반 사용자(USER) 인증 endpoint 모음.
 *
 * <ul>
 *   <li>{@code POST /api/v1/users/auth/signup} — 회원가입 (S1)</li>
 *   <li>{@code POST /api/v1/users/auth/login} — 로그인 (S2 도입, S3에서 Cookie 흐름으로 전환)</li>
 * </ul>
 *
 * <p>상인/관리자 로그인은 별도 컨트롤러로 분리 예정 (S5).
 * reissue/logout 같은 공통 토큰 처리 endpoint는 {@code AuthTokenController}에서 담당.
 */
@Tag(name = "USER 인증", description = "회원가입·로그인 (POST /api/v1/users/auth/**)")
@RestController
@RequestMapping("/api/v1/users/auth")
@RequiredArgsConstructor
public class UserAuthController {

    private final SignupService signupService;
    private final LoginService loginService;
    private final RefreshCookieFactory refreshCookieFactory;

    /**
     * 회원가입. 변경 없음 (S1에서 정의).
     */
    @Operation(summary = "회원가입")
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        Account account = signupService.signup(request);
        return ApiResponse.success(SignupResponse.from(account));
    }

    /**
     * 로그인.
     *
     * <p>S2와 응답 형태가 달라진다 (breaking):
     * <ul>
     *   <li>Body: {@link LoginResponse}는 {@code accessToken}, {@code role}만 포함 (refreshToken 제거)</li>
     *   <li>Header: {@code Set-Cookie}로 refresh 쿠키 전달 (HttpOnly + Secure(prod) + SameSite=Lax + Path=/api/v1/auth)</li>
     * </ul>
     *
     * <p>클라이언트는 Cookie를 직접 다루지 않는다. 브라우저가 자동으로 다음 요청에 첨부.
     */
    @Operation(summary = "USER 로그인")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        // 도메인 서비스에 위임. requiredRole=USER로 고정 (이 컨트롤러가 USER 전용이므로)
        TokenPair pair = loginService.login(request.email(), request.password(), Role.USER);

        // Refresh를 쿠키로 직렬화. 정책(이름/Secure/SameSite/Path/MaxAge)은 RefreshCookieFactory가 캡슐화
        ResponseCookie refreshCookie = refreshCookieFactory.create(pair.refreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponse.success(LoginResponse.from(pair)));
    }
}
