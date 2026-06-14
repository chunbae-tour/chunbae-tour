package com.chunbaetour.domain.chat.controller;

import com.chunbaetour.domain.chat.dto.request.CreateChatRoomRequest;
import com.chunbaetour.domain.chat.dto.request.TransferOwnerRequest;
import com.chunbaetour.domain.chat.dto.response.ChatMessageResponse;
import com.chunbaetour.domain.chat.dto.response.ChatRoomDetailResponse;
import com.chunbaetour.domain.chat.dto.response.ChatRoomMemberResponse;
import com.chunbaetour.domain.chat.dto.response.CreateChatRoomResponse;
import com.chunbaetour.domain.chat.dto.response.MyChatRoomResponse;
import com.chunbaetour.domain.chat.service.ChatMessageService;
import com.chunbaetour.domain.chat.service.ChatRoomService;
import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
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

@Tag(name = "채팅방", description = "채팅방 생성·조회·종료·퇴장·강퇴·방장위임·메시지 조회 (/api/v1/chat/rooms/**)")
@RestController
@RequestMapping("/api/v1/chat/rooms")
@RequiredArgsConstructor
@Validated
public class ChatRoomController {

    private final ChatRoomService chatRoomService;
    private final ChatMessageService chatMessageService;

    @Operation(summary = "채팅방 생성")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateChatRoomResponse> createRoom(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateChatRoomRequest request) {
        return ApiResponse.success(chatRoomService.createRoom(userId, request));
    }

    @Operation(summary = "내 채팅방 목록 조회")
    @GetMapping
    public ApiResponse<CursorPageResponse<MyChatRoomResponse>> getMyRooms(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String cursor,
            @Min(1) @Max(100) @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(chatRoomService.getMyRooms(userId, cursor, size));
    }

    @Operation(summary = "채팅방 상세 조회")
    @GetMapping("/{roomId}")
    public ApiResponse<ChatRoomDetailResponse> getRoomDetail(
            @AuthenticationPrincipal Long userId,
            @Min(1) @PathVariable Long roomId) {
        return ApiResponse.success(chatRoomService.getRoomDetail(userId, roomId));
    }

    @Operation(summary = "방장 위임")
    @PatchMapping("/{roomId}/owner")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void transferOwner(
            @AuthenticationPrincipal Long userId,
            @Min(1) @PathVariable Long roomId,
            @Valid @RequestBody TransferOwnerRequest request) {
        chatRoomService.transferOwner(userId, roomId, request.newOwnerId());
    }

    @Operation(summary = "채팅방 종료")
    @PatchMapping("/{roomId}/close")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void closeRoom(
            @AuthenticationPrincipal Long userId,
            @Min(1) @PathVariable Long roomId) {
        chatRoomService.closeRoom(userId, roomId);
    }

    @Operation(summary = "채팅방 참여자 목록 조회")
    @GetMapping("/{roomId}/members")
    public ApiResponse<List<ChatRoomMemberResponse>> getMembers(
            @AuthenticationPrincipal Long userId,
            @Min(1) @PathVariable Long roomId) {
        return ApiResponse.success(chatRoomService.getMembers(userId, roomId));
    }

    @Operation(summary = "참여자 강퇴")
    @DeleteMapping("/{roomId}/members/{targetUserId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void kickMember(
            @AuthenticationPrincipal Long userId,
            @Min(1) @PathVariable Long roomId,
            @Min(1) @PathVariable Long targetUserId) {
        chatRoomService.kickMember(userId, roomId, targetUserId);
    }

    @Operation(summary = "채팅방 퇴장")
    @DeleteMapping("/{roomId}/members/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leaveRoom(
            @AuthenticationPrincipal Long userId,
            @Min(1) @PathVariable Long roomId) {
        chatRoomService.leaveRoom(userId, roomId);
    }

    @Operation(summary = "채팅 메시지 내역 조회")
    @GetMapping("/{roomId}/messages")
    public ApiResponse<CursorPageResponse<ChatMessageResponse>> getMessages(
            @AuthenticationPrincipal Long userId,
            @Min(1) @PathVariable Long roomId,
            @RequestParam(required = false) String cursor,
            @Min(1) @Max(100) @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.success(chatMessageService.getMessages(userId, roomId, cursor, size));
    }
}
