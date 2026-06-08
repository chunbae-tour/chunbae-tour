package com.chunbaetour.domain.place.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.place.dto.response.RegionResponse;
import com.chunbaetour.domain.place.service.ReverseGeocodingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

@Validated
@RestController
@RequestMapping("/api/v1/places/region")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ReverseGeocodingController {

    private final ReverseGeocodingService reverseGeocodingService;

    /**
     * 좌표(위경도)를 행정구역 주소로 변환
     *
     * @param lat 위도 (-90 ~ 90)
     * @param lng 경도 (-180 ~ 180)
     */
    @GetMapping
    public ApiResponse<RegionResponse> reverseGeocode(
            @RequestParam @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
            @RequestParam @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double lng
    ) {
        RegionResponse response = reverseGeocodingService.reverseGeocode(lat, lng);
        return ApiResponse.success(response);
    }
}
