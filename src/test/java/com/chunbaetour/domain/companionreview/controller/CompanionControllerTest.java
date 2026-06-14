package com.chunbaetour.domain.companionreview.controller;

import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.auth.Role;
import com.chunbaetour.domain.auth.jwt.TokenIssuer;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.companionreview.service.CompanionService;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
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
class CompanionControllerTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenIssuer tokenIssuer;
    @MockitoBean private CompanionService companionService;

    // ===== DELETE /api/v1/chat/rooms/{roomId}/companion =====

    // 미인증 → 401
    @Test
    @DisplayName("동행 취소 미인증 → 401")
    void cancelCompanion_whenUnauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/api/v1/chat/rooms/10/companion"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(companionService);
    }

    // 정상 취소 → 204 No Content
    @Test
    @DisplayName("동행 취소 성공 → 204")
    void cancelCompanion_success_returns204() throws Exception {
        willDoNothing().given(companionService).cancelCompanion(1L, 10L);
        String token = tokenIssuer.issueAccess(1L, Role.USER, "owner@test.com");

        mockMvc.perform(delete("/api/v1/chat/rooms/10/companion")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        verify(companionService).cancelCompanion(1L, 10L);
    }

    // 방장 아님 → CHAT_006(403)
    @Test
    @DisplayName("동행 취소 방장 아님 → 403")
    void cancelCompanion_notOwner_returns403() throws Exception {
        willThrow(new BusinessException(ErrorCode.CHAT_SETTING_FORBIDDEN))
                .given(companionService).cancelCompanion(2L, 10L);
        String token = tokenIssuer.issueAccess(2L, Role.USER, "other@test.com");

        mockMvc.perform(delete("/api/v1/chat/rooms/10/companion")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.CHAT_SETTING_FORBIDDEN.getCode()));
    }

    // 이미 종료된 동행 → CR_006(409)
    @Test
    @DisplayName("동행 취소 이미 종료됨 → 409")
    void cancelCompanion_alreadyEnded_returns409() throws Exception {
        willThrow(new BusinessException(ErrorCode.COMPANION_ALREADY_ENDED))
                .given(companionService).cancelCompanion(1L, 10L);
        String token = tokenIssuer.issueAccess(1L, Role.USER, "owner@test.com");

        mockMvc.perform(delete("/api/v1/chat/rooms/10/companion")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.COMPANION_ALREADY_ENDED.getCode()));
    }

    // roomId 0 이하 → @Positive 검증 실패, 400
    @Test
    @DisplayName("동행 취소 roomId 0 → 400")
    void cancelCompanion_invalidRoomId_returns400() throws Exception {
        String token = tokenIssuer.issueAccess(1L, Role.USER, "owner@test.com");

        mockMvc.perform(delete("/api/v1/chat/rooms/0/companion")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(companionService);
    }

    // 채팅방 미존재 → CHAT_001(404)
    @Test
    @DisplayName("동행 취소 채팅방 미존재 → 404")
    void cancelCompanion_roomNotFound_returns404() throws Exception {
        willThrow(new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND))
                .given(companionService).cancelCompanion(1L, 999L);
        String token = tokenIssuer.issueAccess(1L, Role.USER, "owner@test.com");

        mockMvc.perform(delete("/api/v1/chat/rooms/999/companion")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.CHAT_ROOM_NOT_FOUND.getCode()));
    }

    // 동행 미존재 → CR_005(404)
    @Test
    @DisplayName("동행 취소 동행 미존재 → 404")
    void cancelCompanion_companionNotFound_returns404() throws Exception {
        willThrow(new BusinessException(ErrorCode.COMPANION_NOT_FOUND))
                .given(companionService).cancelCompanion(1L, 10L);
        String token = tokenIssuer.issueAccess(1L, Role.USER, "owner@test.com");

        mockMvc.perform(delete("/api/v1/chat/rooms/10/companion")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.COMPANION_NOT_FOUND.getCode()));
    }
}
