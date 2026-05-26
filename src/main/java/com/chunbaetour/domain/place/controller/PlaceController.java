package com.chunbaetour.domain.place.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.place.dto.request.NearbyPlaceRequest;
import com.chunbaetour.domain.place.dto.response.NearbyPlacePageResponse;
import com.chunbaetour.domain.place.dto.response.PlaceDetailResponse;
import com.chunbaetour.domain.place.service.PlaceLikeService;
import com.chunbaetour.domain.place.service.PlaceService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/places")
@RequiredArgsConstructor
public class PlaceController {

    private final PlaceService placeService;
    private final PlaceLikeService placeLikeService;

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
    @ResponseStatus(HttpStatus.CREATED)
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
    public ApiResponse<Void> removeLike(
            @PathVariable Long placeId,
            @AuthenticationPrincipal Long userId) {
        placeLikeService.removeLike(userId, placeId);
        return ApiResponse.success(null);
    }
}
