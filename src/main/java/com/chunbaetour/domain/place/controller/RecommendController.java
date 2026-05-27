package com.chunbaetour.domain.place.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.place.dto.response.RecommendPlaceResponse;
import com.chunbaetour.domain.place.service.RecommendService;
import com.chunbaetour.domain.place.type.PlaceCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/recommend")
public class RecommendController {

    private final RecommendService recommendService;

    /**
     * 인기 관광지 추천 (Top 10)
     */
    @GetMapping("/popular")
    public ApiResponse<List<RecommendPlaceResponse>> getPopularRecommendations() {
        return ApiResponse.success(recommendService.getPopularRecommendations());
    }

    /**
     * 반경 내 주변 관광지 추천 (랜덤 샘플링)
     */
    @GetMapping("/nearby")
    public ApiResponse<List<RecommendPlaceResponse>> getNearbyRecommendations(
            @RequestParam("lat") @Min(-90) @Max(90) double lat,
            @RequestParam("lng") @Min(-180) @Max(180) double lng,
            @RequestParam(value = "radius", defaultValue = "5.0") @Min(1) @Max(50) double radius,
            @RequestParam(value = "limit", defaultValue = "5") @Min(1) @Max(20) int limit) {
        return ApiResponse.success(recommendService.getNearbyRecommendations(lat, lng, radius, limit));
    }

    /**
     * 카테고리별 관광지 추천
     */
    @GetMapping("/category")
    public ApiResponse<List<RecommendPlaceResponse>> getCategoryRecommendations(
            @RequestParam("category") PlaceCategory category) {
        return ApiResponse.success(recommendService.getCategoryRecommendations(category));
    }
}
