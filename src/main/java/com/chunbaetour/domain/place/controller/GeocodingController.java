package com.chunbaetour.domain.place.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.place.dto.response.GeocodingResponse;
import com.chunbaetour.domain.place.service.GeocodingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 주소 → 좌표 변환(지오코딩) API.
 * Base URL: {@code /api/v1/places/geocoding}
 *
 * <p>로그인 필수: SecurityConfig에서 이 경로를 {@code GET /api/v1/places/**} permitAll보다
 * 먼저 선언해 인증 필수로 고정한다.
 */
@Tag(name = "관광지", description = "관광지 조회·찜·주변 상점·추천 (/api/v1/places/**)")
@RestController
@RequestMapping("/api/v1/places/geocoding")
@RequiredArgsConstructor
@Validated
public class GeocodingController {

    private final GeocodingService geocodingService;

    /**
     * 주소를 좌표로 변환한다.
     *
     * <p>사용 예시:
     * <pre>
     *   GET /api/v1/places/geocoding?query=서울 종로구 사직로 161
     * </pre>
     *
     * @param query 변환할 주소 (1~100자)
     * @return 정제된 주소명, 위도(lat), 경도(lng)
     */
    @Operation(
            summary = "주소 → 좌표 변환 (지오코딩)",
            description = "입력한 주소 문자열을 카카오 API를 통해 위도·경도로 변환합니다. 로그인 필수."
    )
    @GetMapping
    public ApiResponse<GeocodingResponse> geocode(
            @RequestParam
            @NotBlank(message = "주소를 입력해주세요.")
            @Size(max = 100, message = "주소는 최대 100자까지 입력 가능합니다.")
            String query
    ) {
        return ApiResponse.success(geocodingService.geocode(query));
    }
}
