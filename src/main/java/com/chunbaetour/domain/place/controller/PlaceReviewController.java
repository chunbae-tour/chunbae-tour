package com.chunbaetour.domain.place.controller;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.place.dto.request.ReviewCreateRequest;
import com.chunbaetour.domain.place.dto.response.PlaceReviewResponse;
import com.chunbaetour.domain.place.service.PlaceReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관광지 리뷰 컨트롤러.
 *
 * <ul>
 *   <li>{@code POST /api/v1/places/{placeId}/reviews} — 리뷰 작성 (인증 필요, USER)</li>
 *   <li>{@code GET  /api/v1/places/{placeId}/reviews} — 리뷰 목록 조회 (비로그인 허용)</li>
 * </ul>
 *
 * <p>본인 식별: {@code @AuthenticationPrincipal Long userId}로 SecurityContext에서 추출.
 */
@RestController
@RequestMapping("/api/v1/places/{placeId}/reviews")
@RequiredArgsConstructor
public class PlaceReviewController {

    private final PlaceReviewService placeReviewService;

    /**
     * 관광지 리뷰 작성.
     * POST /api/v1/places/{placeId}/reviews
     *
     * <p>인증 필요 (USER). SecurityConfig에서 보호.
     * <ul>
     *   <li>1인 1리뷰 정책: 이미 리뷰를 작성한 경우 PLACE_012 응답</li>
     *   <li>별점 1~5, 내용 최대 500자, 이미지 최대 5장</li>
     *   <li>리뷰 저장 후 관광지 평점(rating), 리뷰 수(reviewCount) 즉시 재계산</li>
     * </ul>
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PlaceReviewResponse> createReview(
            @PathVariable @Positive Long placeId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ReviewCreateRequest request) {
        requireAuthenticated(userId);
        PlaceReviewResponse response = placeReviewService.createReview(userId, placeId, request);
        return ApiResponse.success(response);
    }

    /**
     * 관광지 리뷰 목록 조회 (최신순 페이징).
     * GET /api/v1/places/{placeId}/reviews
     *
     * <p>비로그인 허용 (permitAll).
     * 기본 페이지 크기 10, 최신순 정렬.
     */
    @GetMapping
    public ApiResponse<Page<PlaceReviewResponse>> getPlaceReviews(
            @PathVariable @Positive Long placeId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<PlaceReviewResponse> response = placeReviewService.getPlaceReviews(placeId, pageable);
        return ApiResponse.success(response);
    }

    /**
     * SecurityConfig에서 hasRole("USER")로 인증된 이후 정상 흐름에서 userId가 null이 올 수 없지만,
     * 필터 우회/설정 누락 등 비정상 상태 방어 차원에서 명시적으로 코드로도 확인.
     * NPE 대신 표준 AUTH_006 응답으로 통일.
     */
    private static void requireAuthenticated(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
    }
}
