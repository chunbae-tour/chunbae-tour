package com.chunbaetour.domain.festival.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.festival.dto.request.FestivalCreateRequest;
import com.chunbaetour.domain.festival.dto.request.FestivalUpdateRequest;
import com.chunbaetour.domain.festival.dto.response.FestivalAdminMutateResponse;
import com.chunbaetour.domain.festival.dto.response.FestivalResponse;
import com.chunbaetour.domain.festival.service.FestivalService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

/**
 * 관리자 축제 관리 API (KAN-95).
 * 2중 방어 — SecurityConfig의 /api/v1/admin/** ADMIN 설정 + 클래스 레벨 @PreAuthorize.
 */
@Validated
@RestController
@RequestMapping("/api/v1/admin/festivals")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminFestivalController {

    private final FestivalService festivalService;

    /** 관리자 축제 목록 조회 (HIDDEN 포함, DELETED 제외). */
    @GetMapping
    public ApiResponse<CursorPageResponse<FestivalResponse>> getAdminList(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(festivalService.getAdminList(cursor, size));
    }

    /** 축제 등록. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FestivalAdminMutateResponse> create(
            @Valid @RequestBody FestivalCreateRequest request) {
        return ApiResponse.success(festivalService.create(request));
    }

    /** 축제 수정 (PUT — 전체 교체). */
    @PutMapping("/{festivalId}")
    public ApiResponse<FestivalAdminMutateResponse> update(
            @PathVariable Long festivalId,
            @Valid @RequestBody FestivalUpdateRequest request) {
        return ApiResponse.success(festivalService.update(festivalId, request));
    }

    /** 축제 삭제 (Soft Delete — status = DELETED). */
    @DeleteMapping("/{festivalId}")
    public ApiResponse<Void> delete(@PathVariable Long festivalId) {
        festivalService.delete(festivalId);
        return ApiResponse.success();
    }
}
