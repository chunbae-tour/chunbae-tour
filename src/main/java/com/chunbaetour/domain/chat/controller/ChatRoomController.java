package com.chunbaetour.domain.chat.controller;

import com.chunbaetour.domain.chat.dto.request.CreateChatRoomRequest;
import com.chunbaetour.domain.chat.dto.response.ChatRoomDetailResponse;
import com.chunbaetour.domain.chat.dto.response.CreateChatRoomResponse;
import com.chunbaetour.domain.chat.dto.response.MyChatRoomResponse;
import com.chunbaetour.domain.chat.service.ChatRoomService;
import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat/rooms")
@RequiredArgsConstructor
@Validated
public class ChatRoomController {

    private final ChatRoomService chatRoomService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateChatRoomResponse> createRoom(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateChatRoomRequest request) {
        return ApiResponse.success(chatRoomService.createRoom(userId, request));
    }

    @GetMapping
    public ApiResponse<CursorPageResponse<MyChatRoomResponse>> getMyRooms(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String cursor,
            @Min(1) @Max(100) @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(chatRoomService.getMyRooms(userId, cursor, size));
    }

    @GetMapping("/{roomId}")
    public ApiResponse<ChatRoomDetailResponse> getRoomDetail(
            @AuthenticationPrincipal Long userId,
            @Min(1) @PathVariable Long roomId) {
        return ApiResponse.success(chatRoomService.getRoomDetail(userId, roomId));
    }

    @PatchMapping("/{roomId}/close")
    public ApiResponse<Void> closeRoom(
            @AuthenticationPrincipal Long userId,
            @Min(1) @PathVariable Long roomId) {
        chatRoomService.closeRoom(userId, roomId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{roomId}/members/me")
    public ApiResponse<Void> leaveRoom(
            @AuthenticationPrincipal Long userId,
            @Min(1) @PathVariable Long roomId) {
        chatRoomService.leaveRoom(userId, roomId);
        return ApiResponse.success();
    }
}
