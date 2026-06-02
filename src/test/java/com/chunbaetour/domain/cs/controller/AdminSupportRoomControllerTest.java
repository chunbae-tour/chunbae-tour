package com.chunbaetour.domain.cs.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.auth.Role;
import com.chunbaetour.domain.auth.jwt.TokenIssuer;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.cs.dto.response.AdminSupportRoomResponse;
import com.chunbaetour.domain.cs.dto.response.SupportMessageResponse;
import com.chunbaetour.domain.cs.dto.response.SupportRoomResponse;
import com.chunbaetour.domain.cs.entity.SupportMessageType;
import com.chunbaetour.domain.cs.entity.SupportRoomStatus;
import com.chunbaetour.domain.cs.entity.SupportSenderRole;
import com.chunbaetour.domain.cs.service.SupportRoomService;
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
class AdminSupportRoomControllerTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenIssuer tokenIssuer;

    @MockitoBean private SupportRoomService supportRoomService;

    private static final String BASE_URL = "/api/v1/admin/support/rooms";

    // ===== GET /admin/support/rooms =====

    // ADMIN 인증 → 200
    @Test
    @DisplayName("전체 상담방 목록 — ADMIN 200")
    void getAllRooms_whenAdmin_returns200() throws Exception {
        AdminSupportRoomResponse room = new AdminSupportRoomResponse(
                30L, 1005L, "제주사랑", SupportRoomStatus.WAITING, null, LocalDateTime.now());
        given(supportRoomService.getAllRooms(any(), eq(20), isNull()))
                .willReturn(new CursorPageResponse<>(List.of(room), null, false, 1));
        String token = tokenIssuer.issueAccess(1L, Role.ADMIN, "admin@test.com");

        mockMvc.perform(get(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].supportRoomId").value(30))
                .andExpect(jsonPath("$.data.content[0].userNickname").value("제주사랑"));
    }

    // USER 인증 → 403 (ADMIN 전용)
    @Test
    @DisplayName("전체 상담방 목록 — USER 403")
    void getAllRooms_whenUser_returns403() throws Exception {
        String token = tokenIssuer.issueAccess(1L, Role.USER, "user@test.com");
        mockMvc.perform(get(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.ACCESS_DENIED.getCode()));
        verifyNoInteractions(supportRoomService);
    }

    // 미인증 → 401
    @Test
    @DisplayName("전체 상담방 목록 — 미인증 401")
    void getAllRooms_whenUnauthenticated_returns401() throws Exception {
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTHENTICATION_REQUIRED.getCode()));
        verifyNoInteractions(supportRoomService);
    }

    // ===== GET /admin/support/rooms/{id}/messages =====

    // ADMIN 인증 → 200
    @Test
    @DisplayName("상담 메시지 조회 (ADMIN) — 200")
    void getMessages_whenAdmin_returns200() throws Exception {
        SupportMessageResponse msg = new SupportMessageResponse(
                1L, 1L, SupportSenderRole.CUSTOMER, SupportMessageType.TEXT, "문의입니다", null, LocalDateTime.now());
        given(supportRoomService.getMessagesAsAdmin(eq(10L), any(), eq(20)))
                .willReturn(new CursorPageResponse<>(List.of(msg), null, false, 1));
        String token = tokenIssuer.issueAccess(1L, Role.ADMIN, "admin@test.com");

        mockMvc.perform(get(BASE_URL + "/10/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].messageId").value(1));
    }

    // supportRoomId 0 → 400
    @Test
    @DisplayName("상담 메시지 조회 (ADMIN) — supportRoomId 0 → 400")
    void getMessages_whenRoomIdZero_returns400() throws Exception {
        String token = tokenIssuer.issueAccess(1L, Role.ADMIN, "admin@test.com");
        mockMvc.perform(get(BASE_URL + "/0/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(supportRoomService);
    }

    // USER 인증 → 403 (ADMIN 전용)
    @Test
    @DisplayName("상담 메시지 조회 (ADMIN) — USER 403")
    void getMessages_whenUser_returns403() throws Exception {
        String token = tokenIssuer.issueAccess(1L, Role.USER, "user@test.com");
        mockMvc.perform(get(BASE_URL + "/10/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.ACCESS_DENIED.getCode()));
        verifyNoInteractions(supportRoomService);
    }

    // ===== POST /admin/support/rooms/{id}/close =====

    // ADMIN 인증 → 200 + 종료된 방 반환
    @Test
    @DisplayName("상담 종료 — ADMIN 200")
    void closeRoom_whenAdmin_returns200() throws Exception {
        SupportRoomResponse closed = new SupportRoomResponse(
                10L, 1L, 1L, SupportRoomStatus.CLOSED, "해결 완료", LocalDateTime.now(), LocalDateTime.now());
        given(supportRoomService.closeRoom(eq(10L), any()))
                .willReturn(closed);
        String token = tokenIssuer.issueAccess(1L, Role.ADMIN, "admin@test.com");

        mockMvc.perform(post(BASE_URL + "/10/close")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"해결 완료\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.summary").value("해결 완료"));
    }

    // 이미 CLOSED → 409 CS_002
    @Test
    @DisplayName("상담 종료 — 이미 CLOSED 409")
    void closeRoom_whenAlreadyClosed_returns409() throws Exception {
        willThrow(new BusinessException(ErrorCode.SUPPORT_ROOM_ALREADY_CLOSED))
                .given(supportRoomService).closeRoom(eq(10L), any());
        String token = tokenIssuer.issueAccess(1L, Role.ADMIN, "admin@test.com");

        mockMvc.perform(post(BASE_URL + "/10/close")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUPPORT_ROOM_ALREADY_CLOSED.getCode()));
    }

    // supportRoomId 0 → 400
    @Test
    @DisplayName("상담 종료 — supportRoomId 0 → 400")
    void closeRoom_whenRoomIdZero_returns400() throws Exception {
        String token = tokenIssuer.issueAccess(1L, Role.ADMIN, "admin@test.com");
        mockMvc.perform(post(BASE_URL + "/0/close")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(supportRoomService);
    }

    // USER 인증 → 403
    @Test
    @DisplayName("상담 종료 — USER 403")
    void closeRoom_whenUser_returns403() throws Exception {
        String token = tokenIssuer.issueAccess(1L, Role.USER, "user@test.com");
        mockMvc.perform(post(BASE_URL + "/10/close")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.ACCESS_DENIED.getCode()));
        verifyNoInteractions(supportRoomService);
    }

}
