package com.chunbaetour.domain.festival.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.festival.dto.response.FestivalResponse;
import com.chunbaetour.domain.festival.service.FestivalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "축제", description = "축제 목록·상세 조회 API — 비인증 허용 (/api/v1/festivals/**)")
@Validated
@RestController
@RequestMapping("/api/v1/festivals")
@RequiredArgsConstructor
public class FestivalController {

    private final FestivalService festivalService;

    @Operation(summary = "축제 목록 조회", description = "date 포함 축제·지역 필터, cursor 페이징. ACTIVE 축제만 반환.")
    @GetMapping
    public ApiResponse<CursorPageResponse<FestivalResponse>> getList(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return ApiResponse.success(festivalService.getList(date, region, cursor, size));
    }

    @Operation(summary = "축제 상세 조회", description = "ACTIVE 축제만 반환. HIDDEN/DELETED는 404.")
    @GetMapping("/{festivalId}")
    public ApiResponse<FestivalResponse> getDetail(@Positive @PathVariable Long festivalId) {
        return ApiResponse.success(festivalService.getDetail(festivalId));
    }
}
