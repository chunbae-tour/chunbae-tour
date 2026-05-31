package com.chunbaetour.domain.cs.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.auth.Role;
import com.chunbaetour.domain.auth.jwt.TokenIssuer;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.cs.dto.response.FaqResponse;
import com.chunbaetour.domain.cs.service.FaqService;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
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
class AdminFaqControllerTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenIssuer tokenIssuer;

    @MockitoBean private FaqService faqService;

    private static final String BASE_URL = "/api/v1/admin/faqs";

    // ===== GET /admin/faqs =====

    // ADMIN 인증 → 200 + FAQ 목록 반환
    @Test
    @DisplayName("ADMIN 인증 → FAQ 목록 200")
    void getAll_whenAdmin_returns200() throws Exception {
        FaqResponse faq = buildFaqResponse(1L);
        given(faqService.getAll(any(), eq(20)))
                .willReturn(new CursorPageResponse<>(List.of(faq), null, false, 1));
        String token = tokenIssuer.issueAccess(1L, Role.ADMIN, "admin@test.com");

        mockMvc.perform(get(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].faqId").value(1));
    }

    // 미인증 → 401
    @Test
    @DisplayName("미인증 → 401")
    void getAll_whenUnauthenticated_returns401() throws Exception {
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(faqService);
    }

    // USER 인증 → 403 (ADMIN 전용)
    @Test
    @DisplayName("USER 인증 → 403")
    void getAll_whenUser_returns403() throws Exception {
        String token = tokenIssuer.issueAccess(1L, Role.USER, "user@test.com");
        mockMvc.perform(get(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
        verifyNoInteractions(faqService);
    }

    // ===== POST /admin/faqs =====

    // ADMIN 정상 등록 → 201
    @Test
    @DisplayName("ADMIN FAQ 등록 → 201")
    void create_whenAdmin_returns201() throws Exception {
        given(faqService.create(any())).willReturn(buildFaqResponse(1L));
        String token = tokenIssuer.issueAccess(1L, Role.ADMIN, "admin@test.com");

        mockMvc.perform(post(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"질문","answer":"답변","category":"PAYMENT"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.faqId").value(1));
    }

    // question blank → 400
    @Test
    @DisplayName("question blank → 400")
    void create_whenQuestionBlank_returns400() throws Exception {
        String token = tokenIssuer.issueAccess(1L, Role.ADMIN, "admin@test.com");

        mockMvc.perform(post(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"","answer":"답변","category":"PAYMENT"}
                                """))
                .andExpect(status().isBadRequest());
    }

    // ===== PATCH /admin/faqs/{faqId} =====

    // 존재하지 않는 FAQ 수정 → FAQ_NOT_FOUND 404
    @Test
    @DisplayName("존재하지 않는 FAQ 수정 → 404")
    void update_whenFaqNotFound_returns404() throws Exception {
        willThrow(new BusinessException(ErrorCode.FAQ_NOT_FOUND))
                .given(faqService).update(eq(999L), any());
        String token = tokenIssuer.issueAccess(1L, Role.ADMIN, "admin@test.com");

        mockMvc.perform(patch(BASE_URL + "/999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"수정"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FAQ_001"));
    }

    // ===== DELETE /admin/faqs/{faqId} =====

    // ADMIN soft delete → 204
    @Test
    @DisplayName("ADMIN soft delete → 204")
    void delete_whenAdmin_returns204() throws Exception {
        String token = tokenIssuer.issueAccess(1L, Role.ADMIN, "admin@test.com");

        mockMvc.perform(delete(BASE_URL + "/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    // 존재하지 않는 FAQ 삭제 → FAQ_NOT_FOUND 404
    @Test
    @DisplayName("존재하지 않는 FAQ 삭제 → 404")
    void delete_whenFaqNotFound_returns404() throws Exception {
        willThrow(new BusinessException(ErrorCode.FAQ_NOT_FOUND))
                .given(faqService).delete(999L);
        String token = tokenIssuer.issueAccess(1L, Role.ADMIN, "admin@test.com");

        mockMvc.perform(delete(BASE_URL + "/999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FAQ_001"));
    }

    private FaqResponse buildFaqResponse(Long id) {
        return new FaqResponse(id, "테스트 질문", "테스트 답변", "PAYMENT", true, LocalDateTime.now(), LocalDateTime.now());
    }
}
