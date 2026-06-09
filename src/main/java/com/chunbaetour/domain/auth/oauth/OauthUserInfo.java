package com.chunbaetour.domain.auth.oauth;

import com.chunbaetour.domain.auth.OauthProvider;

/**
 * 소셜 공급자(카카오/네이버) 응답을 정규화한 사용자 식별 정보.
 *
 * <p>신원 식별의 핵심은 {@code provider + oauthId} 두 값뿐이다. {@code email}/{@code nickname}은 공급자가
 * 동의 범위에 따라 주지 않을 수 있어(특히 카카오 이메일) <b>nullable</b>이며, 신규 가입 시 사용자가 폼에서
 * 직접 입력하는 값이 정본이다(중복 판별은 전화번호 기준). 여기서는 prefill 힌트로만 쓴다.
 */
public record OauthUserInfo(
        OauthProvider provider,
        String oauthId,
        String email,
        String nickname
) {
}
