package com.chunbaetour.domain.shop.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.shop.dto.response.SettlementResponse;
import com.chunbaetour.domain.shop.service.SettlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 상인 정산 컨트롤러.
 * /api/v1/merchants/** 경로는 SecurityConfig에서 MERCHANT 권한 필수로 설정됨.
 */
@Tag(name = "정산 (MERCHANT)", description = "정산 신청·내역 조회 (/api/v1/merchants/me/shops/{shopId}/settlements/**)")
@RestController
@RequestMapping("/api/v1/merchants/me/shops/{shopId}/settlements")
@RequiredArgsConstructor
@Validated
public class SettlementController {

    private final SettlementService settlementService;

    @Operation(summary = "정산 신청")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SettlementResponse> requestSettlement(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long shopId) {
        return ApiResponse.success(settlementService.requestSettlement(userId, shopId));
    }

    @Operation(summary = "내 정산 내역 조회")
    @GetMapping
    public ApiResponse<CursorPageResponse<SettlementResponse>> getMySettlements(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long shopId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(settlementService.getMySettlements(userId, shopId, cursor, size));
    }
}
