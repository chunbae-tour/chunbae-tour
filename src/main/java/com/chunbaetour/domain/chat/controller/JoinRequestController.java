package com.chunbaetour.domain.chat.controller;

import com.chunbaetour.domain.chat.dto.request.CreateJoinRequestRequest;
import com.chunbaetour.domain.chat.dto.response.ApproveJoinRequestResponse;
import com.chunbaetour.domain.chat.dto.response.CreateJoinRequestResponse;
import com.chunbaetour.domain.chat.dto.response.JoinRequestResponse;
import com.chunbaetour.domain.chat.service.JoinRequestService;
import com.chunbaetour.domain.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat/rooms")
@RequiredArgsConstructor
@Validated
public class JoinRequestController {

    private final JoinRequestService joinRequestService;

    // 참여 신청 목록 조회 — 방장 전용, PENDING 상태 신청만 반환
    @GetMapping("/{chatRoomId}/join-requests")
    public ApiResponse<List<JoinRequestResponse>> getJoinRequests(
            @AuthenticationPrincipal Long userId,
            @Min(1) @PathVariable Long chatRoomId) {
        return ApiResponse.success(
                joinRequestService.getJoinRequests(userId, chatRoomId));
    }

    // 채팅방 참여 신청 — OPEN 방에 한해 신청, 분산 락으로 TOCTOU 방지
    @PostMapping("/{chatRoomId}/join-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateJoinRequestResponse> createJoinRequest(
            @AuthenticationPrincipal Long userId,
            @Min(1) @PathVariable Long chatRoomId,
            @Valid @RequestBody CreateJoinRequestRequest request) {
        return ApiResponse.success(
                joinRequestService.createJoinRequest(userId, chatRoomId, request));
    }

    // 참여 신청 수락 — 방장 전용, 분산 락으로 정원 TOCTOU 방지
    @PatchMapping("/join-requests/{requestId}/approve")
    public ApiResponse<ApproveJoinRequestResponse> approveJoinRequest(
            @AuthenticationPrincipal Long userId,
            @Min(1) @PathVariable Long requestId) {
        return ApiResponse.success(
                joinRequestService.approveJoinRequest(userId, requestId));
    }
}
