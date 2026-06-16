package com.chunbaetour.domain.companionreview.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.companionreview.dto.request.CompanionAddParticipantsRequest;
import com.chunbaetour.domain.companionreview.dto.request.CompanionCreateRequest;
import com.chunbaetour.domain.companionreview.dto.response.CompanionAddParticipantsResponse;
import com.chunbaetour.domain.companionreview.dto.response.CompanionCreateResponse;
import com.chunbaetour.domain.companionreview.dto.response.CompanionDetailResponse;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "동행", description = "동행 생성/조회/취소/참여자 추가/참여 종료 (/api/v1/chat/rooms/{roomId}/companion)")
@RestController
@RequiredArgsConstructor
@Validated
public class CompanionController {

    private final CompanionService companionService;

    // GET /api/v1/chat/rooms/{roomId}/companion — 동행 상세 조회 (채팅방 ACTIVE 멤버 전용)
    @Operation(summary = "동행 상세 조회", description = "채팅방의 동행 정보(상태, 여행 기간, 참여자 목록, 각 참여자의 endedAt)를 반환합니다. ACTIVE 멤버만 조회 가능(CHAT_005). 동행이 없으면 CR_005.")
    @GetMapping("/api/v1/chat/rooms/{roomId}/companion")
    public ResponseEntity<ApiResponse<CompanionDetailResponse>> getCompanion(
            @AuthenticationPrincipal Long userId,
            @PathVariable @Positive Long roomId) {
        return ResponseEntity.ok(ApiResponse.success(companionService.getCompanion(userId, roomId)));
    }

    // POST /api/v1/chat/rooms/{roomId}/companion — 동행 생성 (방장 전용)
    @Operation(summary = "동행 생성")
    @PostMapping("/api/v1/chat/rooms/{roomId}/companion")
    public ResponseEntity<ApiResponse<CompanionCreateResponse>> createCompanion(
            @AuthenticationPrincipal Long ownerId,
            @PathVariable @Positive Long roomId,
            @Valid @RequestBody CompanionCreateRequest request) {
        CompanionCreateResponse response = companionService.createCompanion(ownerId, roomId, request);
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

    // PATCH /api/v1/chat/rooms/{roomId}/companion/participation/end — 참여자 본인 동행 종료
    @Operation(
            summary = "동행 참여 종료",
            description = "여행이 종료된(Companion.status==ENDED) 동행에서 본인의 참여를 종료 처리합니다. 양쪽 참여자 모두 종료해야 리뷰 작성이 가능합니다."
    )
    @PatchMapping("/api/v1/chat/rooms/{roomId}/companion/participation/end")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void endParticipation(
            @AuthenticationPrincipal Long userId,
            @PathVariable @Positive Long roomId) {
        companionService.endParticipation(userId, roomId);
    }

    // DELETE /api/v1/chat/rooms/{roomId}/companion — 동행 취소 (방장 전용)
    @Operation(
            summary = "동행 취소",
            description = "진행 중인(ONGOING) 동행을 취소합니다. Companion과 참여자 전체가 하드 삭제되며(이력 미보존), 취소 후 같은 채팅방에서 새 동행을 생성할 수 있습니다. 방장 전용이며, 이미 종료된(ENDED) 동행은 거부됩니다."
    )
    @DeleteMapping("/api/v1/chat/rooms/{roomId}/companion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelCompanion(
            @AuthenticationPrincipal Long ownerId,
            @PathVariable @Positive Long roomId) {
        companionService.cancelCompanion(ownerId, roomId);
    }
}
