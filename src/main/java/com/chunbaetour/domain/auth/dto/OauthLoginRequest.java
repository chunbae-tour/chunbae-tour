package com.chunbaetour.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 소셜 로그인 1단계 요청 — 프론트가 카카오/네이버 동의 화면에서 받은 인가코드를 백엔드로 전달.
 *
 * @param code        공급자 authorization code
 * @param redirectUri 인가 요청 시 사용한 redirect URI (공급자가 code 발급 시점과 일치 검증)
 */
public record OauthLoginRequest(
        @NotBlank
        @Size(max = 512)
        String code,

        @NotBlank
        @Size(max = 512)
        String redirectUri
) {
}
