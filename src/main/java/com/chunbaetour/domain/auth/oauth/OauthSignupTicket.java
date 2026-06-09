package com.chunbaetour.domain.auth.oauth;

import com.chunbaetour.domain.auth.OauthProvider;

/**
 * 소셜 신규 가입 티켓의 검증 결과 — 어떤 공급자의 어떤 소셜 계정인지를 담는다.
 *
 * <p>소셜 인증은 통과했으나 아직 우리 계정이 없는 사용자에게 발급되는 단기 서명 토큰의 페이로드.
 * 추가정보 입력(이름/전화/생년월일/이메일/닉네임) 단계에서 이 티켓을 제출해야 가입이 진행된다 —
 * 임의의 oauthId로 계정을 만드는 위조를 차단한다.
 */
public record OauthSignupTicket(OauthProvider provider, String oauthId, String email) {
}
