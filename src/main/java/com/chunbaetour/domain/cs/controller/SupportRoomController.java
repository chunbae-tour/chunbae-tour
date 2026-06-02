package com.chunbaetour.domain.cs.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.cs.dto.request.SupportRoomCreateRequest;
import com.chunbaetour.domain.cs.dto.response.SupportMessageResponse;
import com.chunbaetour.domain.cs.dto.response.SupportRoomResponse;
import com.chunbaetour.domain.cs.entity.SupportRoomStatus;
import com.chunbaetour.domain.cs.service.SupportRoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Support", description = "고객센터 상담 (/api/v1/support/**)")
@RestController
@RequestMapping("/api/v1/support/rooms")
@RequiredArgsConstructor
@Validated
public class SupportRoomController {

    private final SupportRoomService supportRoomService;

    // 상담방 생성 — initialMessage 선택 제공 시 첫 메시지 함께 저장 (USER·MERCHANT 공용)
    @Operation(summary = "상담방 생성 (USER·MERCHANT)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SupportRoomResponse> createRoom(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody SupportRoomCreateRequest request) {
        return ApiResponse.success(supportRoomService.createRoom(userId, request));
    }

    // 내 상담방 목록 cursor 페이징 — status 필터 선택 (USER·MERCHANT 공용)
    @Operation(summary = "내 상담방 목록 조회 (USER·MERCHANT)")
    @GetMapping("/me")
    public ApiResponse<CursorPageResponse<SupportRoomResponse>> getMyRooms(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) SupportRoomStatus status) {
        return ApiResponse.success(supportRoomService.getMyRooms(userId, cursor, size, status));
    }

    // 상담방 메시지 cursor 페이징 — 본인 방만 접근 (USER·MERCHANT)
    @Operation(summary = "상담 메시지 조회 (USER·MERCHANT)")
    @GetMapping("/{supportRoomId}/messages")
    public ApiResponse<CursorPageResponse<SupportMessageResponse>> getMessages(
            @AuthenticationPrincipal Long userId,
            @PathVariable @Positive Long supportRoomId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(supportRoomService.getMessages(userId, supportRoomId, cursor, size));
    }
}
