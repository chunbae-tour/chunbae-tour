package com.chunbaetour.domain.chat.controller;

import com.chunbaetour.domain.chat.dto.request.CreateChatRoomRequest;
import com.chunbaetour.domain.chat.dto.response.CreateChatRoomResponse;
import com.chunbaetour.domain.chat.service.ChatRoomService;
import com.chunbaetour.domain.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat/rooms")
@RequiredArgsConstructor
public class ChatRoomController {

    private final ChatRoomService chatRoomService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateChatRoomResponse> createRoom(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateChatRoomRequest request) {
        return ApiResponse.success(chatRoomService.createRoom(userId, request));
    }
}
