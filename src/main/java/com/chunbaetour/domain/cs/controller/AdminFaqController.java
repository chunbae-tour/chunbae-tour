package com.chunbaetour.domain.cs.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.cs.dto.request.FaqCreateRequest;
import com.chunbaetour.domain.cs.dto.request.FaqUpdateRequest;
import com.chunbaetour.domain.cs.dto.response.FaqResponse;
import com.chunbaetour.domain.cs.service.FaqService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

@Tag(name = "Admin FAQ", description = "FAQ 관리 — 등록·수정·삭제·목록 조회 (/api/v1/admin/faqs/**)")
@RestController
@RequestMapping("/api/v1/admin/faqs")
@RequiredArgsConstructor
@Validated
public class AdminFaqController {

    private final FaqService faqService;

    // FAQ 목록 조회 — 활성/비활성 포함, cursor 페이징
    @Operation(summary = "FAQ 목록 조회 (ADMIN)")
    @GetMapping
    public ApiResponse<CursorPageResponse<FaqResponse>> getAll(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(faqService.getAll(cursor, size));
    }

    // FAQ 등록
    @Operation(summary = "FAQ 등록 (ADMIN)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FaqResponse> create(@Valid @RequestBody FaqCreateRequest request) {
        return ApiResponse.success(faqService.create(request));
    }

    // FAQ 수정 — question/answer/category 부분 수정 가능
    @Operation(summary = "FAQ 수정 (ADMIN)")
    @PutMapping("/{faqId}")
    public ApiResponse<FaqResponse> update(
            @PathVariable Long faqId,
            @Valid @RequestBody FaqUpdateRequest request) {
        return ApiResponse.success(faqService.update(faqId, request));
    }

    // FAQ 삭제 — soft delete (isActive=false), DB 레코드 유지
    @Operation(summary = "FAQ 삭제 (ADMIN)")
    @DeleteMapping("/{faqId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long faqId) {
        faqService.delete(faqId);
    }
}
