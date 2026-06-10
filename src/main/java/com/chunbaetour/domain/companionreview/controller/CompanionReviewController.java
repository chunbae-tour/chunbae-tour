package com.chunbaetour.domain.companionreview.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.companionreview.dto.request.CompanionReviewCreateRequest;
import com.chunbaetour.domain.companionreview.dto.response.CompanionReviewCreateResponse;
import com.chunbaetour.domain.companionreview.dto.response.CompanionReviewResponse;
import com.chunbaetour.domain.companionreview.dto.response.CompanionScoreResponse;
import com.chunbaetour.domain.companionreview.service.CompanionReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "동행 리뷰", description = "동행 리뷰 등록 및 동행 점수 조회 (/api/v1/companion-reviews, /api/v1/users/{userId}/companion-score)")
@RestController
@RequiredArgsConstructor
@Validated
public class CompanionReviewController {

    private final CompanionReviewService companionReviewService;

    // POST /api/v1/companion-reviews — 동행 리뷰 등록 (USER 전용)
    @Operation(summary = "동행 리뷰 등록")
    @PostMapping("/api/v1/companion-reviews")
    public ResponseEntity<ApiResponse<CompanionReviewCreateResponse>> createReview(
            @AuthenticationPrincipal Long reviewerId,
            @Valid @RequestBody CompanionReviewCreateRequest request) {
        CompanionReviewCreateResponse response = companionReviewService.createReview(reviewerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    // GET /api/v1/users/{userId}/companion-score — 동행 점수 조회 (공개)
    @Operation(summary = "동행 점수 조회")
    @GetMapping("/api/v1/users/{userId}/companion-score")
    public ResponseEntity<ApiResponse<CompanionScoreResponse>> getCompanionScore(
            @PathVariable @Positive Long userId) {
        return ResponseEntity.ok(ApiResponse.success(companionReviewService.getCompanionScore(userId)));
    }

    // GET /api/v1/users/{userId}/companion-reviews — 동행 리뷰 목록 조회 (USER 전용, id DESC 커서 페이징)
    @Operation(summary = "동행 리뷰 목록 조회")
    @GetMapping("/api/v1/users/{userId}/companion-reviews")
    public ResponseEntity<ApiResponse<CursorPageResponse<CompanionReviewResponse>>> getReviews(
            @PathVariable @Positive Long userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(ApiResponse.success(companionReviewService.getReviews(userId, cursor, size)));
    }
}
