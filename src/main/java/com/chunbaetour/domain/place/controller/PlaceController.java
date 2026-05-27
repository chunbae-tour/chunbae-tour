package com.chunbaetour.domain.place.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.place.dto.request.NearbyPlaceRequest;
import com.chunbaetour.domain.place.dto.response.NearbyPlacePageResponse;
import com.chunbaetour.domain.place.dto.response.NearbyShopResponse;
import com.chunbaetour.domain.place.dto.response.PlaceDetailResponse;
import com.chunbaetour.domain.place.dto.response.RecommendPlaceResponse;
import com.chunbaetour.domain.place.service.PlaceLikeService;
import com.chunbaetour.domain.place.service.PlaceService;
import com.chunbaetour.domain.place.service.RecommendService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.List;

@RestController
@RequestMapping("/api/v1/places")
@RequiredArgsConstructor
@Validated
public class PlaceController {

    private final PlaceService placeService;
    private final PlaceLikeService placeLikeService;
    private final RecommendService recommendService;

    @GetMapping("/nearby")
    public ApiResponse<NearbyPlacePageResponse> getNearbyPlaces(@Valid @ModelAttribute NearbyPlaceRequest request) {
        NearbyPlacePageResponse response = placeService.findNearby(
                request.lat(),
                request.lng(),
                request.radius(),
                request.cursor(),
                request.cursorDistance(),
                request.size()
        );
        return ApiResponse.success(response);
    }

    /**
     * 관광지 상세 조회
     * GET /api/v1/places/{placeId}
     * <p>
     * - 비로그인 허용 (permitAll). 로그인 시 isLiked 반영.
     * - @AuthenticationPrincipal은 JWT 필터가 SecurityContext에 userId(Long)를 넣으므로 Long으로 주입.
     *   비로그인 요청(Bearer 토큰 없음)이면 null이 주입된다.
     */
    @GetMapping("/{placeId}")
    public ApiResponse<PlaceDetailResponse> getPlaceDetail(
            @PathVariable Long placeId,
            @AuthenticationPrincipal Long userId) {
        PlaceDetailResponse response = placeService.findById(placeId, userId);
        return ApiResponse.success(response);
    }

    /**
     * 관광지 찜하기
     * POST /api/v1/places/{placeId}/like
     * <p>
     * - 인증 필요 (USER). SecurityConfig에서 보호.
     * - 중복 찜 시 PLACE_010 에러 응답.
     */
    @PostMapping("/{placeId}/like")
    public ApiResponse<Void> addLike(
            @PathVariable Long placeId,
            @AuthenticationPrincipal Long userId) {
        placeLikeService.addLike(userId, placeId);
        return ApiResponse.success(null);
    }

    /**
     * 관광지 찜 취소
     * DELETE /api/v1/places/{placeId}/like
     * <p>
     * - 인증 필요 (USER). SecurityConfig에서 보호.
     * - 찜하지 않은 경우 PLACE_011 에러 응답.
     */
    @DeleteMapping("/{placeId}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> removeLike(
            @PathVariable Long placeId,
            @AuthenticationPrincipal Long userId) {
        placeLikeService.removeLike(userId, placeId);
        return ApiResponse.success(null);
    }

    /**
     * 4-2. 특정 관광지 기반 추천
     * GET /api/v1/places/{placeId}/recommend
     * <p>
     * - 동일 카테고리 + 거리순 가까운 곳 TOP 5 (자신 제외)
     * - 비로그인 허용 (permitAll)
     */
    @GetMapping("/{placeId}/recommend")
    public ApiResponse<List<RecommendPlaceResponse>> getPlaceBasedRecommendations(
            @PathVariable Long placeId) {
        return ApiResponse.success(recommendService.getPlaceBasedRecommendations(placeId));
    }
    /**
     * 4-3. 특정 관광지 기반 주변 상점 조회
     * GET /api/v1/places/{placeId}/nearby-shops
     * <p>
     * - 비로그인 허용 (permitAll)
     */
    @GetMapping("/{placeId}/nearby-shops")
    public ApiResponse<List<NearbyShopResponse>> getNearbyShops(
            @PathVariable Long placeId,
            @RequestParam(defaultValue = "5") @Min(1) @Max(50) int limit) {
        return ApiResponse.success(recommendService.getNearbyShops(placeId, limit));
    }
}
