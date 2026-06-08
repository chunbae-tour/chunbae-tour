package com.chunbaetour.domain.chat.controller;

import com.chunbaetour.domain.chat.dto.request.CreateJoinRequestRequest;
import com.chunbaetour.domain.chat.dto.response.ApproveJoinRequestResponse;
import com.chunbaetour.domain.chat.dto.response.CreateJoinRequestResponse;
import com.chunbaetour.domain.chat.dto.response.JoinRequestResponse;
import com.chunbaetour.domain.chat.dto.response.RejectJoinRequestResponse;
import com.chunbaetour.domain.chat.service.JoinRequestService;
import com.chunbaetour.domain.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "채팅 참여 신청", description = "채팅방 참여 신청·목록·수락·거절·취소 (/api/v1/chat/rooms/{chatRoomId}/join-requests/**)")
@RestController
@RequestMapping("/api/v1/chat/rooms")
@RequiredArgsConstructor
@Validated
public class JoinRequestController {

    private final JoinRequestService joinRequestService;

    @Operation(summary = "참여 신청 목록 조회")
    @GetMapping("/{chatRoomId}/join-requests")
    public ApiResponse<List<JoinRequestResponse>> getJoinRequests(
            @AuthenticationPrincipal Long userId,
            @Min(1) @PathVariable Long chatRoomId) {
        return ApiResponse.success(
                joinRequestService.getJoinRequests(userId, chatRoomId));
    }

    @Operation(summary = "채팅방 참여 신청")
    @PostMapping("/{chatRoomId}/join-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateJoinRequestResponse> createJoinRequest(
            @AuthenticationPrincipal Long userId,
            @Min(1) @PathVariable Long chatRoomId,
            @Valid @RequestBody CreateJoinRequestRequest request) {
        return ApiResponse.success(
                joinRequestService.createJoinRequest(userId, chatRoomId, request));
    }

    @Operation(summary = "참여 신청 수락")
    @PostMapping("/{chatRoomId}/join-requests/{requestId}/approve")
    public ApiResponse<ApproveJoinRequestResponse> approveJoinRequest(
            @AuthenticationPrincipal Long userId,
            @Min(1) @PathVariable Long chatRoomId,
            @Min(1) @PathVariable Long requestId) {
        return ApiResponse.success(
                joinRequestService.approveJoinRequest(userId, chatRoomId, requestId));
    }

    @Operation(summary = "참여 신청 취소")
    @DeleteMapping("/{chatRoomId}/join-requests/{requestId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelJoinRequest(
            @AuthenticationPrincipal Long userId,
            @Min(1) @PathVariable Long chatRoomId,
            @Min(1) @PathVariable Long requestId) {
        joinRequestService.cancelJoinRequest(userId, chatRoomId, requestId);
    }

    @Operation(summary = "참여 신청 거절")
    @PostMapping("/{chatRoomId}/join-requests/{requestId}/reject")
    public ApiResponse<RejectJoinRequestResponse> rejectJoinRequest(
            @AuthenticationPrincipal Long userId,
            @Min(1) @PathVariable Long chatRoomId,
            @Min(1) @PathVariable Long requestId) {
        return ApiResponse.success(
                joinRequestService.rejectJoinRequest(userId, chatRoomId, requestId));
    }
}
