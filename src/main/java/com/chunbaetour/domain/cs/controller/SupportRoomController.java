package com.chunbaetour.domain.cs.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.cs.dto.request.SupportRoomCreateRequest;
import com.chunbaetour.domain.cs.dto.response.SupportRoomResponse;
import com.chunbaetour.domain.cs.service.SupportRoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Support", description = "고객센터 상담 (/api/v1/support/**)")
@RestController
@RequestMapping("/api/v1/support/rooms")
@RequiredArgsConstructor
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
}
