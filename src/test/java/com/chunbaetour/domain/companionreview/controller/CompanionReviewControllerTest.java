package com.chunbaetour.domain.companionreview.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import com.chunbaetour.domain.companionreview.dto.response.CompanionScoreResponse;
import com.chunbaetour.domain.companionreview.service.CompanionReviewService;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.time.LocalDateTime;
import java.util.Map;
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
class CompanionReviewControllerTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenIssuer tokenIssuer;
    @MockitoBean private CompanionReviewService companionReviewService;

    // ===== POST /api/v1/companion-reviews =====

    // 미인증 → 401
    @Test
    @DisplayName("리뷰 등록 미인증 → 401")
    void createReview_whenUnauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/companion-reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chatRoomId\":1,\"targetUserId\":2,\"score\":5}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(companionReviewService);
    }

    // 자기 자신 리뷰 → CR_002
    @Test
    @DisplayName("자기 자신 리뷰 → CR_002")
    void createReview_selfReview_returns400() throws Exception {
        given(companionReviewService.createReview(eq(1L), any()))
                .willThrow(new BusinessException(ErrorCode.COMPANION_REVIEW_SELF_NOT_ALLOWED));
        String token = tokenIssuer.issueAccess(1L, Role.USER, "user@test.com");

        mockMvc.perform(post("/api/v1/companion-reviews")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chatRoomId\":1,\"targetUserId\":1,\"score\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.COMPANION_REVIEW_SELF_NOT_ALLOWED.getCode()));
    }

    // 비참여자 리뷰 → CR_003
    @Test
    @DisplayName("채팅방 비참여자 리뷰 → CR_003")
    void createReview_notMember_returns403() throws Exception {
        given(companionReviewService.createReview(eq(1L), any()))
                .willThrow(new BusinessException(ErrorCode.COMPANION_REVIEW_NOT_MEMBER));
        String token = tokenIssuer.issueAccess(1L, Role.USER, "user@test.com");

        mockMvc.perform(post("/api/v1/companion-reviews")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chatRoomId\":1,\"targetUserId\":2,\"score\":5}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.COMPANION_REVIEW_NOT_MEMBER.getCode()));
    }

    // ===== GET /api/v1/users/{userId}/companion-score =====

    // 공개 엔드포인트 — 미인증 200
    @Test
    @DisplayName("동행 점수 조회 미인증 → 200")
    void getCompanionScore_unauthenticated_returns200() throws Exception {
        CompanionScoreResponse response = new CompanionScoreResponse(
                1L, "테스트유저", 4.5f, 2, Map.of("1", 0L, "2", 0L, "3", 0L, "4", 0L, "5", 2L));
        given(companionReviewService.getCompanionScore(1L)).willReturn(response);

        mockMvc.perform(get("/api/v1/users/1/companion-score"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(1L))
                .andExpect(jsonPath("$.data.averageScore").value(4.5));
    }

    // userId = 0 → 400 (@Positive 검증)
    @Test
    @DisplayName("userId = 0 → 400 (@Positive)")
    void getCompanionScore_invalidUserId_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/users/0/companion-score"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(companionReviewService);
    }

    // 존재하지 않는 유저 → 404
    @Test
    @DisplayName("존재하지 않는 유저 → 404")
    void getCompanionScore_userNotFound_returns404() throws Exception {
        given(companionReviewService.getCompanionScore(999L))
                .willThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

        mockMvc.perform(get("/api/v1/users/999/companion-score"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.USER_NOT_FOUND.getCode()));
    }
}
