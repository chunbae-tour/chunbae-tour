package com.chunbaetour.domain.cs.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chunbaetour.domain.auth.Role;
import com.chunbaetour.domain.auth.jwt.TokenIssuer;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.cs.dto.request.SupportRoomCreateRequest;
import com.chunbaetour.domain.cs.dto.response.SupportRoomResponse;
import com.chunbaetour.domain.cs.entity.SupportRoomStatus;
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

    private SupportRoomResponse buildResponse(Long id) {
        return new SupportRoomResponse(id, 1L, null, SupportRoomStatus.WAITING, LocalDateTime.now());
    }
}
