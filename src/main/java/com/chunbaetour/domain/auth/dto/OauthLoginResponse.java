package com.chunbaetour.domain.auth.dto;

import com.chunbaetour.domain.auth.Role;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 소셜 로그인 응답.
 *
 * <ul>
 *   <li>기존 소셜 계정: {@code needSignup=false} + {@code accessToken}/{@code role} (refresh는 쿠키로 전달).</li>
 *   <li>신규: {@code needSignup=true} + {@code signupTicket} + prefill(email/nickname, nullable).
 *       프론트는 이 티켓과 사용자 입력(이름/전화/생년월일/이메일/닉네임)으로 {@code POST /oauth/signup} 호출.</li>
 * </ul>
 */
public record OauthLoginResponse(
        boolean needSignup,
        String accessToken,
        Role role,
        String signupTicket,
        @Schema(nullable = true, description = "공급자 제공 이메일 prefill — 공급자가 이메일을 제공하지 않으면(카카오 이메일 미동의 등) null")
        String email,
        String nickname
) {

    public static OauthLoginResponse loggedIn(String accessToken, Role role) {
        return new OauthLoginResponse(false, accessToken, role, null, null, null);
    }

    public static OauthLoginResponse needSignup(String signupTicket, String email, String nickname) {
        return new OauthLoginResponse(true, null, null, signupTicket, email, nickname);
    }
}
