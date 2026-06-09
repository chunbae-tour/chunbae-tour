package com.chunbaetour.domain.auth.oauth;

import com.chunbaetour.domain.auth.jwt.TokenPair;

/**
 * 소셜 로그인 1단계 처리 결과 — 컨트롤러가 응답/쿠키로 변환한다.
 *
 * <ul>
 *   <li>기존 계정: {@code tokenPair != null} → 로그인 완료(컨트롤러가 refresh 쿠키 세팅).</li>
 *   <li>신규: {@code tokenPair == null} + {@code signupTicket} → 추가정보 입력 단계로 유도.</li>
 * </ul>
 */
public record OauthLoginResult(
        TokenPair tokenPair,
        String signupTicket,
        String email,
        String nickname
) {

    public boolean needSignup() {
        return tokenPair == null;
    }

    public static OauthLoginResult loggedIn(TokenPair tokenPair) {
        return new OauthLoginResult(tokenPair, null, null, null);
    }

    public static OauthLoginResult needSignup(String signupTicket, String email, String nickname) {
        return new OauthLoginResult(null, signupTicket, email, nickname);
    }
}
