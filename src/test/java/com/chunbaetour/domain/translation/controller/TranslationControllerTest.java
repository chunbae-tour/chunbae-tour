package com.chunbaetour.domain.translation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
            {"content": "안녕", "targetLanguage": "EN"}
            """;

    // USER 인증 + 정상 요청 → 200 + 번역 결과 반환
    @Test
    void translate_whenUser_returns200() throws Exception {
        given(translationService.translate("안녕", LanguageCode.EN))
                .willReturn(new TranslationResponse("Hello", LanguageCode.EN));

        String token = tokenIssuer.issueAccess(1L, Role.USER, "user@test.com");

        mockMvc.perform(post(URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.translatedContent").value("Hello"));
    }

    // 미인증 → 401 AUTH_006
    @Test
    void translate_whenUnauthenticated_returns401() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_006"));
    }

    // MERCHANT 인증 → 403 AUTH_007 (USER 전용 엔드포인트)
    @Test
    void translate_whenMerchant_returns403() throws Exception {
        String token = tokenIssuer.issueAccess(1L, Role.MERCHANT, "merchant@test.com");

        mockMvc.perform(post(URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_007"));
    }

    // content 빈 문자열 → 400 (@NotBlank)
    @Test
    void translate_whenContentBlank_returns400() throws Exception {
        String token = tokenIssuer.issueAccess(1L, Role.USER, "user@test.com");

        mockMvc.perform(post(URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content": "", "targetLanguage": "EN"}
                                """))
                .andExpect(status().isBadRequest());
    }

    // content 1001자 → 400 (@Size max=1000)
    @Test
    void translate_whenContentTooLong_returns400() throws Exception {
        String token = tokenIssuer.issueAccess(1L, Role.USER, "user@test.com");
        String longContent = "a".repeat(1001);

        mockMvc.perform(post(URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content": "%s", "targetLanguage": "EN"}
                                """.formatted(longContent)))
                .andExpect(status().isBadRequest());
    }

    // 잘못된 enum 값 → 400 (Jackson 역직렬화 실패 → GlobalExceptionHandler)
    @Test
    void translate_whenInvalidTargetLanguage_returns400() throws Exception {
        String token = tokenIssuer.issueAccess(1L, Role.USER, "user@test.com");

        mockMvc.perform(post(URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content": "안녕", "targetLanguage": "FR"}
                                """))
                .andExpect(status().isBadRequest());
    }

    // 외부 API 실패 → COMMON_007 (503)
    @Test
    void translate_whenExternalApiFails_returnsCommon007() throws Exception {
        given(translationService.translate(any(), any()))
                .willThrow(new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR));

        String token = tokenIssuer.issueAccess(1L, Role.USER, "user@test.com");

        mockMvc.perform(post(URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("COMMON_007"));
    }
}
