package com.chunbaetour.domain.companionreview.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.auth.Role;
import com.chunbaetour.domain.auth.jwt.TokenIssuer;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.companionreview.dto.response.CompanionDetailResponse;
import com.chunbaetour.domain.companionreview.service.CompanionService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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

    // ===== GET /api/v1/chat/rooms/{roomId}/companion =====

    // 동행 조회 성공 → 200 + status/participants/endedAt 반환
    @Test
    @DisplayName("동행 상세 조회 성공 → 200")
    void getCompanion_success_returns200() throws Exception {
        LocalDateTime participantEndedAt = LocalDateTime.of(2026, 7, 6, 10, 0);
        CompanionDetailResponse response = new CompanionDetailResponse(
                10L, 1L, "ENDED",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 5),
                LocalDateTime.of(2026, 7, 1, 9, 0), LocalDateTime.of(2026, 7, 6, 0, 0),
                List.of(
                        new CompanionDetailResponse.ParticipantInfo(1L, participantEndedAt),
                        new CompanionDetailResponse.ParticipantInfo(2L, null)
                )
        );
        given(companionService.getCompanion(1L, 1L)).willReturn(response);
        String token = tokenIssuer.issueAccess(1L, Role.USER, "user@test.com");

        mockMvc.perform(get("/api/v1/chat/rooms/1/companion")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ENDED"))
                .andExpect(jsonPath("$.data.participants.length()").value(2))
                .andExpect(jsonPath("$.data.participants[0].userId").value(1))
                .andExpect(jsonPath("$.data.participants[0].endedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.participants[1].userId").value(2))
                .andExpect(jsonPath("$.data.participants[1].endedAt").doesNotExist());
    }

    // 동행 없는 방 → CR_005(404)
    @Test
    @DisplayName("동행 상세 조회 동행 미존재 → 404")
    void getCompanion_notFound_returns404() throws Exception {
        willThrow(new BusinessException(ErrorCode.COMPANION_NOT_FOUND))
                .given(companionService).getCompanion(1L, 99L);
        String token = tokenIssuer.issueAccess(1L, Role.USER, "user@test.com");

        mockMvc.perform(get("/api/v1/chat/rooms/99/companion")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.COMPANION_NOT_FOUND.getCode()));
    }

    // 채팅방 비멤버 → CHAT_005(403)
    @Test
    @DisplayName("동행 상세 조회 비멤버 → 403")
    void getCompanion_notMember_returns403() throws Exception {
        willThrow(new BusinessException(ErrorCode.CHAT_NOT_JOINED))
                .given(companionService).getCompanion(2L, 1L);
        String token = tokenIssuer.issueAccess(2L, Role.USER, "outsider@test.com");

        mockMvc.perform(get("/api/v1/chat/rooms/1/companion")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.CHAT_NOT_JOINED.getCode()));
    }

    // ===== PATCH /api/v1/chat/rooms/{roomId}/companion/participation/end =====

    // 정상 종료 → 204 No Content
    @Test
    @DisplayName("동행 참여 종료 성공 → 204")
    void endParticipation_success_returns204() throws Exception {
        willDoNothing().given(companionService).endParticipation(1L, 10L);
        String token = tokenIssuer.issueAccess(1L, Role.USER, "participant@test.com");

        mockMvc.perform(patch("/api/v1/chat/rooms/10/companion/participation/end")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        verify(companionService).endParticipation(1L, 10L);
    }

    // 호출자가 동행 참여자가 아님 → CR_013(403)
    @Test
    @DisplayName("동행 참여 종료 참여자 아님 → 403")
    void endParticipation_notParticipant_returns403() throws Exception {
        willThrow(new BusinessException(ErrorCode.COMPANION_PARTICIPANT_NOT_FOUND))
                .given(companionService).endParticipation(2L, 10L);
        String token = tokenIssuer.issueAccess(2L, Role.USER, "non-participant@test.com");

        mockMvc.perform(patch("/api/v1/chat/rooms/10/companion/participation/end")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.COMPANION_PARTICIPANT_NOT_FOUND.getCode()));
    }

    // 동행이 아직 ENDED 상태가 아님 → CR_014(409)
    @Test
    @DisplayName("동행 참여 종료 여행 미종료 → 409")
    void endParticipation_companionNotEnded_returns409() throws Exception {
        willThrow(new BusinessException(ErrorCode.COMPANION_NOT_ENDED_FOR_PARTICIPATION))
                .given(companionService).endParticipation(1L, 10L);
        String token = tokenIssuer.issueAccess(1L, Role.USER, "participant@test.com");

        mockMvc.perform(patch("/api/v1/chat/rooms/10/companion/participation/end")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.COMPANION_NOT_ENDED_FOR_PARTICIPATION.getCode()));
    }

    // 이미 참여 종료 처리됨 → CR_015(409)
    @Test
    @DisplayName("동행 참여 종료 이미 처리됨 → 409")
    void endParticipation_alreadyEnded_returns409() throws Exception {
        willThrow(new BusinessException(ErrorCode.COMPANION_PARTICIPATION_ALREADY_ENDED))
                .given(companionService).endParticipation(1L, 10L);
        String token = tokenIssuer.issueAccess(1L, Role.USER, "participant@test.com");

        mockMvc.perform(patch("/api/v1/chat/rooms/10/companion/participation/end")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.COMPANION_PARTICIPATION_ALREADY_ENDED.getCode()));
    }

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
