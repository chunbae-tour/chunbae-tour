package com.chunbaetour.domain.place.controller;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.place.dto.response.NearbyCategoryPlacesResponse;
import com.chunbaetour.domain.place.type.NearbyCategory;
import com.chunbaetour.domain.place.dto.request.MapMarkerRequest;
import com.chunbaetour.domain.place.dto.request.NearbyPlaceRequest;
import com.chunbaetour.domain.place.dto.response.MapMarkerPageResponse;
import com.chunbaetour.domain.place.dto.response.MapMarkerResponse;
import com.chunbaetour.domain.place.dto.request.PlaceListRequest;
import com.chunbaetour.domain.place.dto.response.NearbyPlacePageResponse;
import com.chunbaetour.domain.place.dto.response.NearbyShopResponse;
import com.chunbaetour.domain.place.dto.response.PlaceDetailResponse;
import com.chunbaetour.domain.place.dto.response.PlaceListResponse;
import com.chunbaetour.domain.place.dto.response.RecommendPlaceResponse;
import com.chunbaetour.domain.place.service.PlaceLikeService;
import com.chunbaetour.domain.place.service.PlaceService;
import com.chunbaetour.domain.place.service.RecommendService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "관광지", description = "관광지 조회·찜·주변 상점·추천 (/api/v1/places/**)")
@RestController
@RequestMapping("/api/v1/places")
@RequiredArgsConstructor
@Validated
public class PlaceController {

    private final PlaceService placeService;
    private final PlaceLikeService placeLikeService;
    private final RecommendService recommendService;

    /**
     * 관광지 목록 조회 (PHASE 8-2)
     * GET /api/v1/places
     *
     * <p>카테고리 / 지역 필터 + 커서 기반 페이지네이션을 지원합니다.
     * - 비로그인 허용 (permitAll). SecurityConfig의 {@code GET /api/v1/places/**} 규칙이 이미 커버.
     * - 정렬: 평점 내림차순 → ID 내림차순.
     *
     * <p>사용 예시:
     * <pre>
     *   GET /api/v1/places                                            // 전체 목록 (첫 페이지)
     *   GET /api/v1/places?category=TOURIST_SPOT                     // 관광지만 필터링
     *   GET /api/v1/places?region=서귀포                              // 서귀포 지역만
     *   GET /api/v1/places?cursor=50&cursorRating=4.5&size=10        // 커서 페이징 (다음 페이지)
     * </pre>
     * <p>⚠️ cursor와 cursorRating은 반드시 함께 전달해야 합니다 (이전 응답의 nextCursorId, nextCursorRating 사용).
     */
    @SecurityRequirements
    @Operation(summary = "관광지 목록 조회", description = "카테고리/지역 필터 + 커서 페이징 지원. 정렬: 평점 내림차순.")
    @GetMapping
    public ApiResponse<PlaceListResponse> getPlaceList(@Valid @ModelAttribute PlaceListRequest request) {
        return ApiResponse.success(placeService.findList(request));
    }

    /**
     * 지도 마커 일괄 조회 (PHASE 8-1)
     * GET /api/v1/places/map-markers
     *
     * <p>현재 지도 화면의 남서쪽(SW)과 북동쪽(NE) 좌표를 받아 해당 영역(Bounding Box) 안의 관광지 마커들을 조회합니다.
     * 클라이언트 사이드 클러스터링을 위해 최대 500개까지만 응답합니다.
     */
    @SecurityRequirements
    @Operation(summary = "지도 마커 일괄 조회", description = "지도 뷰포트(Bounding Box) 내의 마커 리스트를 조회합니다. (최대 span 2.0도, 결과 500개 초과 시 truncated=true 반환)")
    @GetMapping("/map-markers")
    public ApiResponse<MapMarkerPageResponse> getMapMarkers(@Valid @ModelAttribute MapMarkerRequest request) {
        return ApiResponse.success(placeService.getMapMarkers(request));
    }

    @SecurityRequirements
    @Operation(summary = "주변 관광지 조회")
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
    @SecurityRequirements
    @Operation(summary = "관광지 상세 조회")
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
    @Operation(summary = "관광지 찜 추가")
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
    @Operation(summary = "관광지 찜 취소")
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
    @SecurityRequirements
    @Operation(summary = "관광지 기반 추천")
    @GetMapping("/{placeId}/recommend")
    public ApiResponse<List<RecommendPlaceResponse>> getPlaceBasedRecommendations(
            @PathVariable Long placeId) {
        return ApiResponse.success(recommendService.getPlaceBasedRecommendations(placeId));
    }

    @SecurityRequirements
    @Operation(summary = "관광지 주변 장소(맛집/카페/숙박) 카테고리 검색", description = "카카오 카테고리 API 연동. 반경 내 장소를 반환합니다.")
    @GetMapping("/{placeId}/nearby-places")
    public ApiResponse<NearbyCategoryPlacesResponse> getNearbyCategoryPlaces(
            @PathVariable Long placeId,
            @RequestParam NearbyCategory category,
            @RequestParam(defaultValue = "500") @Min(1) @Max(20000) int radius
    ) {
        return ApiResponse.success(placeService.findNearbyCategoryPlaces(placeId, category, radius));
    }

    /**
     * 4-3. 특정 관광지 기반 주변 상점 조회
     * GET /api/v1/places/{placeId}/nearby-shops
     * <p>
     * - 비로그인 허용 (permitAll)
     */
    @SecurityRequirements
    @Operation(summary = "관광지 주변 상점 조회")
    @GetMapping("/{placeId}/nearby-shops")
    public ApiResponse<List<NearbyShopResponse>> getNearbyShops(
            @PathVariable Long placeId,
            @RequestParam(defaultValue = "5") @Min(1) @Max(50) int limit) {
        return ApiResponse.success(recommendService.getNearbyShops(placeId, limit));
    }
}
