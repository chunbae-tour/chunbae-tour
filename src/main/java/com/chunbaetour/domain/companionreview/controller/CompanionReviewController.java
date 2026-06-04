package com.chunbaetour.domain.companionreview.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.companionreview.dto.request.CompanionReviewCreateRequest;
import com.chunbaetour.domain.companionreview.dto.response.CompanionReviewCreateResponse;
import com.chunbaetour.domain.companionreview.dto.response.CompanionScoreResponse;
import com.chunbaetour.domain.companionreview.service.CompanionReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CompanionReviewController {

    private final CompanionReviewService companionReviewService;

    // POST /api/v1/companion-reviews — 동행 리뷰 등록 (USER 전용)
    @PostMapping("/api/v1/companion-reviews")
    public ResponseEntity<ApiResponse<CompanionReviewCreateResponse>> createReview(
            @AuthenticationPrincipal(expression = "name") String reviewerIdStr,
            @Valid @RequestBody CompanionReviewCreateRequest request) {
        Long reviewerId = Long.parseLong(reviewerIdStr);
        CompanionReviewCreateResponse response = companionReviewService.createReview(reviewerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    // GET /api/v1/users/{userId}/companion-score — 동행 점수 조회 (공개)
    @GetMapping("/api/v1/users/{userId}/companion-score")
    public ResponseEntity<ApiResponse<CompanionScoreResponse>> getCompanionScore(
            @PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(companionReviewService.getCompanionScore(userId)));
    }
}
