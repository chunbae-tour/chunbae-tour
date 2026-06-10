package com.chunbaetour.domain.companionreview.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.companionreview.dto.request.CompanionAddParticipantsRequest;
import com.chunbaetour.domain.companionreview.dto.request.CompanionStartRequest;
import com.chunbaetour.domain.companionreview.dto.response.CompanionAddParticipantsResponse;
import com.chunbaetour.domain.companionreview.dto.response.CompanionEndResponse;
import com.chunbaetour.domain.companionreview.dto.response.CompanionStartResponse;
import com.chunbaetour.domain.companionreview.service.CompanionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "동행", description = "동행 시작/종료/참여자 추가 (/api/v1/chat/rooms/{roomId}/companion)")
@RestController
@RequiredArgsConstructor
@Validated
public class CompanionController {

    private final CompanionService companionService;

    // POST /api/v1/chat/rooms/{roomId}/companion — 동행 시작 (방장 전용)
    @Operation(summary = "동행 시작")
    @PostMapping("/api/v1/chat/rooms/{roomId}/companion")
    public ResponseEntity<ApiResponse<CompanionStartResponse>> startCompanion(
            @AuthenticationPrincipal Long ownerId,
            @PathVariable @Positive Long roomId,
            @Valid @RequestBody CompanionStartRequest request) {
        CompanionStartResponse response = companionService.startCompanion(ownerId, roomId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    // POST /api/v1/chat/rooms/{roomId}/companion/participants — 동행 참여자 추가 (방장 전용)
    @Operation(
            summary = "동행 참여자 추가",
            description = "진행 중인 동행에 채팅방 ACTIVE 멤버를 참여자로 추가합니다. 방장 전용이며, ENDED 동행이거나 채팅방이 CLOSED면 거부됩니다."
    )
    @PostMapping("/api/v1/chat/rooms/{roomId}/companion/participants")
    public ResponseEntity<ApiResponse<CompanionAddParticipantsResponse>> addParticipants(
            @AuthenticationPrincipal Long ownerId,
            @PathVariable @Positive Long roomId,
            @Valid @RequestBody CompanionAddParticipantsRequest request) {
        CompanionAddParticipantsResponse response = companionService.addParticipants(ownerId, roomId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    // PATCH /api/v1/chat/rooms/{roomId}/companion/end — 동행 종료 (방장 전용)
    @Operation(summary = "동행 종료")
    @PatchMapping("/api/v1/chat/rooms/{roomId}/companion/end")
    public ResponseEntity<ApiResponse<CompanionEndResponse>> endCompanion(
            @AuthenticationPrincipal Long ownerId,
            @PathVariable @Positive Long roomId) {
        CompanionEndResponse response = companionService.endCompanion(ownerId, roomId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
