package com.chunbaetour.domain.festival.controller;

import com.chunbaetour.domain.admin.audit.AdminActionType;
import com.chunbaetour.domain.admin.audit.AdminTargetType;
import com.chunbaetour.domain.admin.audit.LogAdminAction;
import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.festival.dto.request.FestivalCreateRequest;
import com.chunbaetour.domain.festival.dto.request.FestivalUpdateRequest;
import com.chunbaetour.domain.festival.dto.response.FestivalAdminMutateResponse;
import com.chunbaetour.domain.festival.dto.response.FestivalResponse;
import com.chunbaetour.domain.festival.service.FestivalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자 - 축제", description = "축제 등록·수정·삭제·조회 관리자 API — ADMIN 전용 (/api/v1/admin/festivals/**)")
@Validated
@RestController
@RequestMapping("/api/v1/admin/festivals")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminFestivalController {

    private final FestivalService festivalService;

    @Operation(summary = "관리자 축제 목록 조회", description = "[ADMIN 전용] HIDDEN 포함 전체. cursor 페이징.")
    @GetMapping
    public ApiResponse<CursorPageResponse<FestivalResponse>> getAdminList(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(festivalService.getAdminList(cursor, size));
    }

    // POST는 생성 전 path id가 없어 returnIdField로 응답 본문의 festivalId를 targetId에 기록(S07 PLACE_CREATE 패턴).
    @Operation(summary = "축제 수동 등록", description = "source=MANUAL. 201 반환.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @LogAdminAction(actionType = AdminActionType.FESTIVAL_CREATE,
            targetType = AdminTargetType.FESTIVAL,
            returnIdField = "festivalId")
    public ApiResponse<FestivalAdminMutateResponse> create(
            @Valid @RequestBody FestivalCreateRequest request) {
        return ApiResponse.success(festivalService.create(request));
    }

    @Operation(summary = "축제 수정", description = "전체 필드 교체 (PUT). MANUAL·API_FETCH 모두 가능.")
    @PutMapping("/{festivalId}")
    @LogAdminAction(actionType = AdminActionType.FESTIVAL_UPDATE,
            targetType = AdminTargetType.FESTIVAL,
            targetIdVar = "festivalId")
    public ApiResponse<FestivalAdminMutateResponse> update(
            @Positive @PathVariable Long festivalId,
            @Valid @RequestBody FestivalUpdateRequest request) {
        return ApiResponse.success(festivalService.update(festivalId, request));
    }

    @Operation(summary = "축제 삭제", description = "Soft delete — status=DELETED.")
    @DeleteMapping("/{festivalId}")
    @LogAdminAction(actionType = AdminActionType.FESTIVAL_DELETE,
            targetType = AdminTargetType.FESTIVAL,
            targetIdVar = "festivalId")
    public ApiResponse<Void> delete(@Positive @PathVariable Long festivalId) {
        festivalService.delete(festivalId);
        return ApiResponse.success();
    }
}
