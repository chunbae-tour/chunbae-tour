package com.chunbaetour.domain.auth.dto;

import com.chunbaetour.domain.auth.Role;
import com.chunbaetour.domain.auth.jwt.TokenPair;

/**
 * 토큰 재발급 응답 Body.
 *
 * <p>Refresh Token은 Body에 절대 포함하지 않는다. {@code Set-Cookie} 헤더로만 전달.
 * (XSS로부터 보호하는 HttpOnly 원칙 일관성 유지)
 *
 * <p>응답 필드:
 * <ul>
 *   <li>{@code accessToken} — 클라이언트가 메모리에 갱신 보관</li>
 *   <li>{@code role} — 권한 변경(USER → MERCHANT 승격 등) 즉시 반영 확인용</li>
 * </ul>
 */
public record ReissueResponse(String accessToken, Role role) {

    /**
     * {@link TokenPair}에서 응답 형태로 변환. refreshToken/refreshTokenId는 Cookie로 분리되므로 제외.
     */
    public static ReissueResponse from(TokenPair pair) {
        return new ReissueResponse(pair.accessToken(), pair.role());
    }
}
