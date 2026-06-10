package com.chunbaetour.domain.translation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.auth.Role;
import com.chunbaetour.domain.auth.jwt.TokenIssuer;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import com.chunbaetour.domain.translation.dto.response.TranslationResponse;
import com.chunbaetour.domain.translation.service.TranslationService;
import com.chunbaetour.domain.translation.type.LanguageCode;
import com.chunbaetour.domain.translation.type.TranslationSourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class TranslationControllerTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenIssuer tokenIssuer;

    @MockitoBean private TranslationService translationService;

    private static final String URL = "/api/v1/translations";
    private static final String VALID_BODY = """
            {"content": "안녕", "targetLanguage": "EN", "sourceType": "CHAT"}
            """;

    // USER 인증 + 정상 요청 → 200 + 번역 결과 반환
    @Test
    @DisplayName("USER 인증 + 정상 요청 → 200 + 번역 결과 반환")
    void translate_whenUser_returns200() throws Exception {
        given(translationService.translate("안녕", LanguageCode.EN, TranslationSourceType.CHAT))
                .willReturn(new TranslationResponse("Hello", LanguageCode.EN));

        String token = tokenIssuer.issueAccess(1L, Role.USER, "user@test.com");

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.translatedContent").value("Hello"))
                .andExpect(jsonPath("$.data.targetLanguage").value("EN"));
    }

    // 정적 도메인(sourceType=FAQ) 요청 → sourceType 그대로 service에 전달, 200
    @Test
    @DisplayName("정적 도메인(sourceType=FAQ) 요청 → 200")
    void translate_whenStaticSourceType_returns200() throws Exception {
        given(translationService.translate("운영시간이 어떻게 되나요?", LanguageCode.EN, TranslationSourceType.FAQ))
                .willReturn(new TranslationResponse("What are the business hours?", LanguageCode.EN));

        String token = tokenIssuer.issueAccess(1L, Role.USER, "user@test.com");

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content": "운영시간이 어떻게 되나요?", "targetLanguage": "EN", "sourceType": "FAQ"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.translatedContent").value("What are the business hours?"));
    }

    // 미인증 → 401 AUTH_006, service 미호출
    @Test
    @DisplayName("미인증 → 401 AUTH_006")
    void translate_whenUnauthenticated_returns401() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_006"));

        verifyNoInteractions(translationService);
    }

    // MERCHANT 인증 → 403 AUTH_007 (USER 전용 엔드포인트), service 미호출
    @Test
    @DisplayName("MERCHANT 인증 → 403 AUTH_007 (USER 전용)")
    void translate_whenMerchant_returns403() throws Exception {
        String token = tokenIssuer.issueAccess(1L, Role.MERCHANT, "merchant@test.com");

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_007"));

        verifyNoInteractions(translationService);
    }

    // content 빈 문자열 → 400 COMMON_002 (@NotBlank), service 미호출
    @Test
    @DisplayName("content 빈 문자열 → 400 COMMON_002 (@NotBlank)")
    void translate_whenContentBlank_returns400() throws Exception {
        String token = tokenIssuer.issueAccess(1L, Role.USER, "user@test.com");

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content": "", "targetLanguage": "EN", "sourceType": "CHAT"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));

        verifyNoInteractions(translationService);
    }

    // content 1001자 → 400 COMMON_002 (@Size max=1000), service 미호출
    @Test
    @DisplayName("content 1001자 → 400 COMMON_002 (@Size max=1000)")
    void translate_whenContentTooLong_returns400() throws Exception {
        String token = tokenIssuer.issueAccess(1L, Role.USER, "user@test.com");
        String longContent = "a".repeat(1001);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content": "%s", "targetLanguage": "EN", "sourceType": "CHAT"}
                                """.formatted(longContent)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));

        verifyNoInteractions(translationService);
    }

    // 잘못된 enum 값 → 400 COMMON_002 (Jackson 역직렬화 실패 → GlobalExceptionHandler), service 미호출
    @Test
    @DisplayName("잘못된 targetLanguage(FR) → 400 COMMON_002")
    void translate_whenInvalidTargetLanguage_returns400() throws Exception {
        String token = tokenIssuer.issueAccess(1L, Role.USER, "user@test.com");

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content": "안녕", "targetLanguage": "FR", "sourceType": "CHAT"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));

        verifyNoInteractions(translationService);
    }

    // sourceType 누락 → 400 COMMON_002 (@NotNull), service 미호출
    @Test
    @DisplayName("sourceType 누락 → 400 COMMON_002 (@NotNull)")
    void translate_whenSourceTypeMissing_returns400() throws Exception {
        String token = tokenIssuer.issueAccess(1L, Role.USER, "user@test.com");

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content": "안녕", "targetLanguage": "EN"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));

        verifyNoInteractions(translationService);
    }

    // 외부 API 실패 → COMMON_007 (503)
    @Test
    @DisplayName("외부 API 실패 → 503 COMMON_007")
    void translate_whenExternalApiFails_returnsCommon007() throws Exception {
        // any(), any(), any() — 이 케이스는 인자 무관하게 어떤 번역 호출이든 실패해야 함
        given(translationService.translate(any(), any(), any()))
                .willThrow(new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR));

        String token = tokenIssuer.issueAccess(1L, Role.USER, "user@test.com");

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("COMMON_007"));
    }
}
