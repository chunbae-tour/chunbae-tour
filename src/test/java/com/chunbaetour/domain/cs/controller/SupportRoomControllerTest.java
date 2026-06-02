package com.chunbaetour.domain.cs.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
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
import com.chunbaetour.domain.cs.dto.request.SupportRoomCreateRequest;
import com.chunbaetour.domain.cs.dto.response.SupportMessageResponse;
import com.chunbaetour.domain.cs.dto.response.SupportRoomResponse;
import com.chunbaetour.domain.cs.entity.SupportMessageType;
import com.chunbaetour.domain.cs.entity.SupportRoomStatus;
import com.chunbaetour.domain.cs.entity.SupportSenderRole;
import java.util.List;
import com.chunbaetour.domain.cs.service.SupportRoomService;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.time.LocalDateTime;
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
class SupportRoomControllerTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenIssuer tokenIssuer;

    @MockitoBean private SupportRoomService supportRoomService;

    private static final String BASE_URL = "/api/v1/support/rooms";

    // ===== POST /support/rooms =====

    // 미인증 → 401 AUTH_006
    @Test
    @DisplayName("미인증 → 401")
    void createRoom_whenUnauthenticated_returns401() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTHENTICATION_REQUIRED.getCode()));
        verifyNoInteractions(supportRoomService);
    }

    // ADMIN 인증 → 403 AUTH_007 (USER·MERCHANT 전용)
    @Test
    @DisplayName("ADMIN 인증 → 403")
    void createRoom_whenAdmin_returns403() throws Exception {
        String token = tokenIssuer.issueAccess(1L, Role.ADMIN, "admin@test.com");
        mockMvc.perform(post(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.ACCESS_DENIED.getCode()));
        verifyNoInteractions(supportRoomService);
    }

    // MERCHANT 인증 → 201 (USER·MERCHANT 공용)
    @Test
    @DisplayName("MERCHANT 인증 → 201")
    void createRoom_whenMerchant_returns201() throws Exception {
        given(supportRoomService.createRoom(eq(1L), any(SupportRoomCreateRequest.class)))
                .willReturn(buildResponse(12L));
        String token = tokenIssuer.issueAccess(1L, Role.MERCHANT, "merchant@test.com");
        mockMvc.perform(post(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.supportRoomId").value(12));
    }

    // USER 인증 + initialMessage 없음 → 201
    @Test
    @DisplayName("USER 인증 + initialMessage 없음 → 201")
    void createRoom_whenUserWithoutMessage_returns201() throws Exception {
        given(supportRoomService.createRoom(eq(1L), any(SupportRoomCreateRequest.class)))
                .willReturn(buildResponse(10L));
        String token = tokenIssuer.issueAccess(1L, Role.USER, "user@test.com");

        mockMvc.perform(post(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.supportRoomId").value(10))
                .andExpect(jsonPath("$.data.status").value("WAITING"));
    }

    // USER 인증 + initialMessage 제공 → 201
    @Test
    @DisplayName("USER 인증 + initialMessage 제공 → 201")
    void createRoom_whenUserWithMessage_returns201() throws Exception {
        given(supportRoomService.createRoom(eq(1L), any(SupportRoomCreateRequest.class)))
                .willReturn(buildResponse(11L));
        String token = tokenIssuer.issueAccess(1L, Role.USER, "user@test.com");

        mockMvc.perform(post(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"initialMessage":"결제가 안 됩니다."}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.supportRoomId").value(11));
    }

    // initialMessage 1000자 초과 → 400
    @Test
    @DisplayName("initialMessage 1001자 → 400")
    void createRoom_whenMessageTooLong_returns400() throws Exception {
        String token = tokenIssuer.issueAccess(1L, Role.USER, "user@test.com");
        String longMessage = "a".repeat(1001);

        mockMvc.perform(post(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"initialMessage\":\"" + longMessage + "\"}"))
                .andExpect(status().isBadRequest());
    }

    // ===== GET /support/rooms/me =====

    // USER 인증 → 200
    @Test
    @DisplayName("내 상담방 목록 — USER 200")
    void getMyRooms_whenUser_returns200() throws Exception {
        given(supportRoomService.getMyRooms(eq(1L), any(), eq(20), isNull()))
                .willReturn(new CursorPageResponse<>(List.of(buildResponse(10L)), null, false, 1));
        String token = tokenIssuer.issueAccess(1L, Role.USER, "user@test.com");

        mockMvc.perform(get(BASE_URL + "/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].supportRoomId").value(10));
    }

    // 미인증 → 401
    @Test
    @DisplayName("내 상담방 목록 — 미인증 401")
    void getMyRooms_whenUnauthenticated_returns401() throws Exception {
        mockMvc.perform(get(BASE_URL + "/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTHENTICATION_REQUIRED.getCode()));
        verifyNoInteractions(supportRoomService);
    }

    // ===== GET /support/rooms/{id}/messages =====

    // USER 인증 → 200
    @Test
    @DisplayName("상담 메시지 조회 — USER 200")
    void getMessages_whenUser_returns200() throws Exception {
        SupportMessageResponse msg = new SupportMessageResponse(
                1L, 1L, SupportSenderRole.CUSTOMER, SupportMessageType.TEXT, "결제 안됩니다", null, LocalDateTime.now());
        given(supportRoomService.getMessages(eq(1L), eq(10L), any(), eq(20)))
                .willReturn(new CursorPageResponse<>(List.of(msg), null, false, 1));
        String token = tokenIssuer.issueAccess(1L, Role.USER, "user@test.com");

        mockMvc.perform(get(BASE_URL + "/10/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].messageId").value(1));
    }

    // MERCHANT 토큰 + 타인 방 → 403 (서비스 ownership check)
    @Test
    @DisplayName("상담 메시지 조회 — MERCHANT 타인 방 403")
    void getMessages_whenMerchantAccessOtherUserRoom_returns403() throws Exception {
        given(supportRoomService.getMessages(eq(2L), eq(10L), any(), eq(20)))
                .willThrow(new BusinessException(ErrorCode.SUPPORT_ROOM_FORBIDDEN));
        String token = tokenIssuer.issueAccess(2L, Role.MERCHANT, "merchant@test.com");

        mockMvc.perform(get(BASE_URL + "/10/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUPPORT_ROOM_FORBIDDEN.getCode()));
    }

    // 미인증 → 401
    @Test
    @DisplayName("상담 메시지 조회 — 미인증 401")
    void getMessages_whenUnauthenticated_returns401() throws Exception {
        mockMvc.perform(get(BASE_URL + "/10/messages"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTHENTICATION_REQUIRED.getCode()));
        verifyNoInteractions(supportRoomService);
    }

    private SupportRoomResponse buildResponse(Long id) {
        return new SupportRoomResponse(id, 1L, null, SupportRoomStatus.WAITING, null, null, LocalDateTime.now());
    }
}
