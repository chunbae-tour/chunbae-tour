package com.chunbaetour.domain.auth;

import com.chunbaetour.domain.auth.dto.LoginRequest;
import com.chunbaetour.domain.auth.dto.LoginResponse;
import com.chunbaetour.domain.auth.dto.SignupRequest;
import com.chunbaetour.domain.auth.dto.SignupResponse;
import com.chunbaetour.domain.auth.jwt.TokenPair;
import com.chunbaetour.domain.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/auth")
@RequiredArgsConstructor
public class UserAuthController {

    private final SignupService signupService;
    private final LoginService loginService;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        Account account = signupService.signup(request);
        return ApiResponse.success(SignupResponse.from(account));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        TokenPair pair = loginService.login(request.email(), request.password(), Role.USER);
        return ApiResponse.success(LoginResponse.from(pair));
    }
}
