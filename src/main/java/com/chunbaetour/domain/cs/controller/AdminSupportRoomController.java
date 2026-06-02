package com.chunbaetour.domain.cs.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.cs.dto.response.AdminSupportRoomResponse;
import com.chunbaetour.domain.cs.dto.response.SupportMessageResponse;
import com.chunbaetour.domain.cs.entity.SupportRoomStatus;
import com.chunbaetour.domain.cs.service.SupportRoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Support", description = "고객센터 상담 관리 (/api/v1/admin/support/**)")
@RestController
@RequestMapping("/api/v1/admin/support/rooms")
@RequiredArgsConstructor
@Validated
public class AdminSupportRoomController {

    private final SupportRoomService supportRoomService;

    // 전체 상담방 목록 cursor 페이징 — status 필터 선택 (ADMIN 전용)
    @Operation(summary = "전체 상담방 목록 조회 (ADMIN)")
    @GetMapping
    public ApiResponse<CursorPageResponse<AdminSupportRoomResponse>> getAllRooms(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) SupportRoomStatus status) {
        return ApiResponse.success(supportRoomService.getAllRooms(cursor, size, status));
    }

    // 상담방 메시지 cursor 페이징 — 소유자 무관 접근 가능 (ADMIN 전용)
    @Operation(summary = "상담 메시지 조회 (ADMIN)")
    @GetMapping("/{supportRoomId}/messages")
    public ApiResponse<CursorPageResponse<SupportMessageResponse>> getMessages(
            @PathVariable @Positive Long supportRoomId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(supportRoomService.getMessagesAsAdmin(supportRoomId, cursor, size));
    }
}
