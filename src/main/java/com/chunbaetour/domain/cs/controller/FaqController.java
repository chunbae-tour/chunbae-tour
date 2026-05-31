package com.chunbaetour.domain.cs.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.cs.dto.response.FaqResponse;
import com.chunbaetour.domain.cs.service.FaqService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "FAQ", description = "버튼형 FAQ 조회 (/api/v1/faqs/**)")
@RestController
@RequestMapping("/api/v1/faqs")
@RequiredArgsConstructor
@Validated
public class FaqController {

    private final FaqService faqService;

    // 활성 FAQ 목록 cursor 페이징 — category 파라미터로 필터링 가능 (USER 전용)
    @Operation(summary = "FAQ 목록 조회 (USER)")
    @GetMapping
    public ApiResponse<CursorPageResponse<FaqResponse>> getFaqs(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String category) {
        return ApiResponse.success(faqService.getActiveFaqs(cursor, size, category));
    }
}
