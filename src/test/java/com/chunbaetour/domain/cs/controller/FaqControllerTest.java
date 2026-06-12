package com.chunbaetour.domain.cs.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.auth.Role;
import com.chunbaetour.domain.auth.jwt.TokenIssuer;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.cs.dto.response.FaqResponse;
import com.chunbaetour.domain.cs.dto.response.FaqTranslationResponse;
import com.chunbaetour.domain.cs.service.FaqService;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import com.chunbaetour.domain.translation.type.LanguageCode;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class FaqControllerTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenIssuer tokenIssuer;

    @MockitoBean private FaqService faqService;

    private static final String BASE_URL = "/api/v1/faqs";

    // ===== GET /faqs =====

    // 미인증 → 401 AUTH_006
    @Test
    @DisplayName("미인증 → 401")
    void getFaqs_whenUnauthenticated_returns401() throws Exception {
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTHENTICATION_REQUIRED.getCode()));
        verifyNoInteractions(faqService);
    }

    // ADMIN 인증 → 403 AUTH_007 (USER 전용 endpoint)
    @Test
    @DisplayName("ADMIN 인증 → 403")
    void getFaqs_whenAdmin_returns403() throws Exception {
        String token = tokenIssuer.issueAccess(1L, Role.ADMIN, "admin@test.com");
        mockMvc.perform(get(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.ACCESS_DENIED.getCode()));
        verifyNoInteractions(faqService);
    }

    // MERCHANT 인증 → 403 AUTH_007 (USER 전용 endpoint)
    @Test
    @DisplayName("MERCHANT 인증 → 403")
    void getFaqs_whenMerchant_returns403() throws Exception {
        String token = tokenIssuer.issueAccess(1L, Role.MERCHANT, "merchant@test.com");
        mockMvc.perform(get(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.ACCESS_DENIED.getCode()));
        verifyNoInteractions(faqService);
    }

    // USER 인증 → 200 + FAQ 필드 검증
    @Test
    @DisplayName("USER 인증 → 200")
    void getFaqs_whenUser_returns200() throws Exception {
        FaqResponse faq = buildFaqResponse(1L);
        given(faqService.getActiveFaqs(any(), eq(20), any()))
                .willReturn(new CursorPageResponse<>(List.of(faq), null, false, 1));
        String token = tokenIssuer.issueAccess(1L, Role.USER, "user@test.com");

        mockMvc.perform(get(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].faqId").value(1))
                .andExpect(jsonPath("$.data.content[0].question").value("테스트 질문"))
                .andExpect(jsonPath("$.data.content[0].category").value("PAYMENT"));
    }

    // category 파라미터 전달 시 서비스로 전달됨
    @Test
    @DisplayName("category 파라미터 → 서비스 전달")
    void getFaqs_withCategory_passesToService() throws Exception {
        given(faqService.getActiveFaqs(any(), eq(20), eq("PAYMENT")))
                .willReturn(new CursorPageResponse<>(List.of(), null, false, 0));
        String token = tokenIssuer.issueAccess(1L, Role.USER, "user@test.com");

        mockMvc.perform(get(BASE_URL)
                        .param("category", "PAYMENT")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    // size 범위 초과(101) → 400
    @Test
    @DisplayName("size=101 → 400")
    void getFaqs_whenSizeExceedsMax_returns400() throws Exception {
        String token = tokenIssuer.issueAccess(1L, Role.USER, "user@test.com");

        mockMvc.perform(get(BASE_URL)
                        .param("size", "101")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    // ===== GET /faqs/{faqId}/translation =====

    // USER 인증 → 200 + 번역된 question/answer 반환
    @Test
    @DisplayName("FAQ 번역 USER 인증 → 200")
    void getFaqTranslation_whenUser_returns200() throws Exception {
        given(faqService.getFaqTranslation(1L, LanguageCode.EN))
                .willReturn(new FaqTranslationResponse(1L, "Test question", "Test answer", LanguageCode.EN));
        String token = tokenIssuer.issueAccess(1L, Role.USER, "user@test.com");

        mockMvc.perform(get(BASE_URL + "/1/translation")
                        .param("targetLanguage", "EN")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.faqId").value(1))
                .andExpect(jsonPath("$.data.question").value("Test question"))
                .andExpect(jsonPath("$.data.answer").value("Test answer"));
    }

    // 존재하지 않는 FAQ → 404 FAQ_001
    @Test
    @DisplayName("FAQ 번역 존재하지 않는 FAQ → 404")
    void getFaqTranslation_whenFaqNotFound_returns404() throws Exception {
        given(faqService.getFaqTranslation(999L, LanguageCode.EN))
                .willThrow(new BusinessException(ErrorCode.FAQ_NOT_FOUND));
        String token = tokenIssuer.issueAccess(1L, Role.USER, "user@test.com");

        mockMvc.perform(get(BASE_URL + "/999/translation")
                        .param("targetLanguage", "EN")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.FAQ_NOT_FOUND.getCode()));
    }

    // 미인증 → 401 AUTH_006
    @Test
    @DisplayName("FAQ 번역 미인증 → 401")
    void getFaqTranslation_whenUnauthenticated_returns401() throws Exception {
        mockMvc.perform(get(BASE_URL + "/1/translation")
                        .param("targetLanguage", "EN"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTHENTICATION_REQUIRED.getCode()));
        verifyNoInteractions(faqService);
    }

    // ADMIN 인증 → 403 AUTH_007
    @Test
    @DisplayName("FAQ 번역 ADMIN 인증 → 403")
    void getFaqTranslation_whenAdmin_returns403() throws Exception {
        String token = tokenIssuer.issueAccess(1L, Role.ADMIN, "admin@test.com");
        mockMvc.perform(get(BASE_URL + "/1/translation")
                        .param("targetLanguage", "EN")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.ACCESS_DENIED.getCode()));
        verifyNoInteractions(faqService);
    }

    // MERCHANT 인증 → 403 AUTH_007
    @Test
    @DisplayName("FAQ 번역 MERCHANT 인증 → 403")
    void getFaqTranslation_whenMerchant_returns403() throws Exception {
        String token = tokenIssuer.issueAccess(1L, Role.MERCHANT, "merchant@test.com");
        mockMvc.perform(get(BASE_URL + "/1/translation")
                        .param("targetLanguage", "EN")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.ACCESS_DENIED.getCode()));
        verifyNoInteractions(faqService);
    }

    private FaqResponse buildFaqResponse(Long id) {
        return new FaqResponse(id, "테스트 질문", "테스트 답변", "PAYMENT", true, LocalDateTime.now(), LocalDateTime.now());
    }
}
