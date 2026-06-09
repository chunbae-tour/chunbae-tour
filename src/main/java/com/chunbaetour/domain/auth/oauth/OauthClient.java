package com.chunbaetour.domain.auth.oauth;

import com.chunbaetour.domain.auth.OauthProvider;

/**
 * 소셜 공급자별 OAuth 클라이언트 — 인가코드(authorization code)를 받아 토큰 교환 후 사용자 정보를 조회한다.
 *
 * <p>흐름: 프론트가 카카오/네이버 동의 화면에서 받은 {@code code}를 백엔드로 전달 → 본 클라이언트가
 * 공급자 토큰 엔드포인트에서 access token으로 교환 → 사용자 정보 엔드포인트 호출 → {@link OauthUserInfo} 정규화.
 * access token이 프론트에 노출되지 않아 토큰 탈취면이 작다.
 */
public interface OauthClient {

    /** 이 클라이언트가 담당하는 공급자. */
    OauthProvider provider();

    /**
     * 인가코드로 토큰 교환 + 사용자 정보 조회.
     *
     * @param code        프론트가 공급자에서 받은 authorization code
     * @param redirectUri 프론트가 인가 요청 시 사용한 redirect URI (공급자가 code 발급 시점과 일치 검증)
     * @return 정규화된 사용자 식별 정보
     * @throws com.chunbaetour.domain.common.error.BusinessException 토큰 교환/조회 실패 시
     *         ({@link com.chunbaetour.domain.common.error.ErrorCode#OAUTH_PROVIDER_ERROR})
     */
    OauthUserInfo fetch(String code, String redirectUri);
}
