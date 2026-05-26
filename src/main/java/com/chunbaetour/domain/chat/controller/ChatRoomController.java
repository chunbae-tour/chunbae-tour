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

    // 채팅방 생성 — 동행 게시글 작성자만 개설 가능, postId 중복 시 CHAT_ROOM_DUPLICATE
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateChatRoomResponse> createRoom(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateChatRoomRequest request) {
        return ApiResponse.success(chatRoomService.createRoom(userId, request));
    }

    // 내 채팅방 목록 — ACTIVE 멤버 상태 기준 커서 페이지네이션, CLOSED 방도 포함
    @GetMapping
    public ApiResponse<CursorPageResponse<MyChatRoomResponse>> getMyRooms(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String cursor,
            @Min(1) @Max(100) @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(chatRoomService.getMyRooms(userId, cursor, size));
    }

    // 채팅방 상세 조회 — ACTIVE 멤버만 접근 가능, 비멤버·강퇴·퇴장은 CHAT_NOT_JOINED
    @GetMapping("/{roomId}")
    public ApiResponse<ChatRoomDetailResponse> getRoomDetail(
            @AuthenticationPrincipal Long userId,
            @Min(1) @PathVariable Long roomId) {
        return ApiResponse.success(chatRoomService.getRoomDetail(userId, roomId));
    }

    // 채팅방 종료 — 방장 전용, room.status만 CLOSED로 전이, 멤버 상태 유지
    @PatchMapping("/{roomId}/close")
    public ApiResponse<Void> closeRoom(
            @AuthenticationPrincipal Long userId,
            @Min(1) @PathVariable Long roomId) {
        chatRoomService.closeRoom(userId, roomId);
        return ApiResponse.success(null);
    }

    // 참여자 강퇴 — 방장 전용, OWNER_ACTIVE 대상 강퇴 불가(CHAT_017), MVP에서 방장 자기 강퇴 불가
    @DeleteMapping("/{roomId}/members/{targetUserId}")
    public ApiResponse<Void> kickMember(
            @AuthenticationPrincipal Long userId,
            @Min(1) @PathVariable Long roomId,
            @Min(1) @PathVariable Long targetUserId) {
        chatRoomService.kickMember(userId, roomId, targetUserId);
        return ApiResponse.success();
    }

    // 채팅방 퇴장 — 방장 퇴장 불가(CHAT_015), leave() 후 currentMembers -1
    @DeleteMapping("/{roomId}/members/me")
    public ApiResponse<Void> leaveRoom(
            @AuthenticationPrincipal Long userId,
            @Min(1) @PathVariable Long roomId) {
        chatRoomService.leaveRoom(userId, roomId);
        return ApiResponse.success();
    }
}
