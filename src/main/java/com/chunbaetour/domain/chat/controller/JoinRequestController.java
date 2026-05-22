package com.chunbaetour.domain.chat.controller;

import com.chunbaetour.domain.chat.dto.request.CreateJoinRequestRequest;
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

    @GetMapping("/{chatRoomId}/join-requests")
    public ApiResponse<List<JoinRequestResponse>> getJoinRequests(
            @AuthenticationPrincipal Long userId,
            @Min(1) @PathVariable Long chatRoomId) {
        return ApiResponse.success(
                joinRequestService.getJoinRequests(userId, chatRoomId));
    }

    @PostMapping("/{chatRoomId}/join-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateJoinRequestResponse> createJoinRequest(
            @AuthenticationPrincipal Long userId,
            @Min(1) @PathVariable Long chatRoomId,
            @Valid @RequestBody CreateJoinRequestRequest request) {
        return ApiResponse.success(
                joinRequestService.createJoinRequest(userId, chatRoomId, request));
    }
}
