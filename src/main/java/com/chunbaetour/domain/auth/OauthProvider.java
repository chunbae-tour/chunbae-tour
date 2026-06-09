package com.chunbaetour.domain.auth;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import java.util.Locale;

/**
 * 소셜 로그인 공급자. {@code users.oauth_provider} 컬럼에 {@link jakarta.persistence.EnumType#STRING}으로 저장된다.
 *
 * <p>로컬(이메일/비밀번호) 계정은 {@code oauth_provider = NULL}로 구분한다 — 별도 LOCAL 값을 두지 않는다.
 */
public enum OauthProvider {
    KAKAO,
    NAVER;

    /**
     * URL path 변수({@code kakao}/{@code naver}, 대소문자 무관)를 enum으로 변환.
     *
     * @throws BusinessException 지원하지 않는 공급자인 경우 ({@link ErrorCode#OAUTH_PROVIDER_UNSUPPORTED}).
     */
    public static OauthProvider fromPath(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.OAUTH_PROVIDER_UNSUPPORTED);
        }
        try {
            return OauthProvider.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.OAUTH_PROVIDER_UNSUPPORTED);
        }
    }
}
