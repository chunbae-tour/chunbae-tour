package com.chunbaetour.domain.auth.oauth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.auth.OauthProvider;
import com.chunbaetour.domain.auth.Role;
import com.chunbaetour.domain.auth.dto.OauthLoginRequest;
import com.chunbaetour.domain.auth.jwt.TokenPair;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link MerchantOAuthController} 통합 테스트 — 상인 소셜 로그인 endpoint의 라우팅·보안(permitAll)·응답 매핑 검증.
 *
 * <p>외부 공급자 HTTP 호출을 포함하는 {@link OauthLoginService}는 {@link MockitoBean}으로 대체한다
 * (role 분기 로직 자체는 {@link OauthLoginServiceTest}가 단위로 검증). 본 테스트는 컨트롤러가
 * {@code requiredRole=MERCHANT}를 전달하는지, 성공 시 토큰 Body + refresh 쿠키를 내려주는지,
 * 서비스가 던진 {@code ACCESS_DENIED}가 403으로 매핑되는지를 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MerchantOAuthControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE_URL = "/api/v1/merchants/auth/oauth";
    private static final String COOKIE_NAME = "refreshToken";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private OauthLoginService oauthLoginService;

    @DisplayName("상인 네이버 소셜 로그인 성공 → 200 + role=MERCHANT + refresh 쿠키 (requiredRole=MERCHANT 전달)")
    @Test
    void merchantNaverLoginSucceeds() throws Exception {
        given(oauthLoginService.login(eq(OauthProvider.NAVER), any(), any(), eq(Role.MERCHANT)))
                .willReturn(OauthLoginResult.loggedIn(
                        new TokenPair("merchant-access", "merchant-refresh", "tid-1", Role.MERCHANT)));

        mockMvc.perform(post(BASE_URL + "/naver")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new OauthLoginRequest("auth-code", "https://app/callback"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.accessToken").value("merchant-access"))
                .andExpect(jsonPath("$.data.role").value("MERCHANT"))
                // refresh는 Body에 없고 HttpOnly 쿠키로만 전달
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        Matchers.allOf(
                                Matchers.containsString(COOKIE_NAME + "="),
                                Matchers.containsString("HttpOnly"))));

        // 컨트롤러가 상인 진입점이므로 requiredRole=MERCHANT를 전달해야 한다
        verify(oauthLoginService).login(eq(OauthProvider.NAVER), eq("auth-code"),
                eq("https://app/callback"), eq(Role.MERCHANT));
    }

    @DisplayName("상인 진입점에 비-MERCHANT 계정(서비스가 ACCESS_DENIED) → 403 AUTH_007")
    @Test
    void roleMismatchReturns403() throws Exception {
        given(oauthLoginService.login(any(), any(), any(), eq(Role.MERCHANT)))
                .willThrow(new BusinessException(ErrorCode.ACCESS_DENIED));

        mockMvc.perform(post(BASE_URL + "/naver")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new OauthLoginRequest("auth-code", "https://app/callback"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.ACCESS_DENIED.getCode()));
    }

    @DisplayName("미인증으로도 호출 가능(permitAll) — 카카오 성공 경로")
    @Test
    void merchantKakaoLoginSucceedsWithoutAuthHeader() throws Exception {
        given(oauthLoginService.login(eq(OauthProvider.KAKAO), any(), any(), eq(Role.MERCHANT)))
                .willReturn(OauthLoginResult.loggedIn(
                        new TokenPair("k-access", "k-refresh", "tid-2", Role.MERCHANT)));

        // Authorization 헤더 없이 호출 — /merchants/auth/** permitAll 검증
        mockMvc.perform(post(BASE_URL + "/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new OauthLoginRequest("auth-code", "https://app/callback"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("MERCHANT"));
    }

    @DisplayName("미지원 공급자 경로 → 400 AUTH_018 (서비스 미진입)")
    @Test
    void unsupportedProviderReturns400() throws Exception {
        mockMvc.perform(post(BASE_URL + "/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new OauthLoginRequest("auth-code", "https://app/callback"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.OAUTH_PROVIDER_UNSUPPORTED.getCode()));

        // path 파싱 단계에서 거부 — 로그인 서비스로 진입하지 않음
        verifyNoInteractions(oauthLoginService);
    }
}
