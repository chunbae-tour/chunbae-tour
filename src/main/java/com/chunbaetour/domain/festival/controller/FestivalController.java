package com.chunbaetour.domain.festival.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.festival.dto.response.FestivalResponse;
import com.chunbaetour.domain.festival.service.FestivalService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자용 축제 API (KAN-97/98). 비인증 허용.
 */
@Validated
@RestController
@RequestMapping("/api/v1/festivals")
@RequiredArgsConstructor
public class FestivalController {

    private final FestivalService festivalService;

    /**
     * 축제 목록 조회 (KAN-97).
     * date: 해당 날짜를 포함하는 축제 필터 (옵션).
     * region: 지역 필터 (옵션).
     */
    @GetMapping
    public ApiResponse<CursorPageResponse<FestivalResponse>> getList(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return ApiResponse.success(festivalService.getList(date, region, cursor, size));
    }

    /**
     * 축제 상세 조회 (KAN-98). ACTIVE 축제만 반환.
     */
    @GetMapping("/{festivalId}")
    public ApiResponse<FestivalResponse> getDetail(@Positive @PathVariable Long festivalId) {
        return ApiResponse.success(festivalService.getDetail(festivalId));
    }
}
