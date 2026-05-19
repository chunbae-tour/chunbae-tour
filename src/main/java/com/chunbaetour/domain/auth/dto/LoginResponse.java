package com.chunbaetour.domain.auth.dto;

import com.chunbaetour.domain.auth.Role;
import com.chunbaetour.domain.auth.jwt.TokenPair;

/**
 * 로그인 응답 Body.
 *
 * <p>S3부터는 Refresh Token이 HttpOnly Cookie로 전달되므로 Body에서 제외한다.
 * 클라이언트는:
 * <ul>
 *   <li>{@code accessToken} — 메모리 보관, Authorization 헤더로 전송</li>
 *   <li>{@code role} — UI 권한 분기용</li>
 *   <li>refreshToken — JavaScript에서 접근 불가 (HttpOnly), 브라우저가 자동으로 다음 요청 시 전송</li>
 * </ul>
 *
 * <p>refreshToken을 Body에 노출하면 XSS 공격자가 추출할 수 있어 위험하다 (S2 → S3 전환의 핵심 보안 개선).
 */
public record LoginResponse(String accessToken, Role role) {

    /**
     * {@link TokenPair}에서 응답 형태로 변환. refreshToken과 refreshTokenId는 의도적으로 누락
     * (Cookie 흐름으로 분리).
     */
    public static LoginResponse from(TokenPair pair) {
        return new LoginResponse(pair.accessToken(), pair.role());
    }
}
