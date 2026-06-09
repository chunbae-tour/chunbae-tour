package com.chunbaetour.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 소셜 로그인 1단계 요청 — 프론트가 카카오/네이버 동의 화면에서 받은 인가코드를 백엔드로 전달.
 *
 * <p><b>CSRF(state) 방어 책임</b>: 본 흐름은 SPA(프론트)가 인가코드를 받아 백엔드로 전달하는 방식이라,
 * OAuth {@code state}(또는 PKCE) 검증은 <b>프론트가 담당</b>한다 — 프론트가 인가 요청 시 임의 state를 생성·
 * 보관(sessionStorage 등)하고, 콜백에서 공급자가 돌려준 state가 일치할 때만 본 API로 code를 전달해야 한다.
 * 백엔드는 미인증 로그인 엔드포인트라 바인딩할 세션이 없어 서버측 state 검증을 강제하지 않는다(서버 발급
 * state 저장 + 검증을 백엔드로 옮기는 강화는 별도 follow-up). 추가 방어로 본 엔드포인트는 IP rate-limit 적용.
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
