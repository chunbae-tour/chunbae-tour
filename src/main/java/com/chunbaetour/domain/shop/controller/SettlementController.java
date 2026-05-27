package com.chunbaetour.domain.shop.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.shop.dto.response.SettlementResponse;
import com.chunbaetour.domain.shop.service.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/v1/merchants/me/settlements")
@RequiredArgsConstructor
@Validated
public class SettlementController {

    private final SettlementService settlementService;

    /** POST /api/v1/merchants/me/settlements — 정산 신청 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MERCHANT')")
    public ApiResponse<SettlementResponse> requestSettlement(
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.success(settlementService.requestSettlement(userId));
    }

    /** GET /api/v1/merchants/me/settlements — 내 정산 내역 조회 */
    @GetMapping
    @PreAuthorize("hasRole('MERCHANT')")
    public ApiResponse<CursorPageResponse<SettlementResponse>> getMySettlements(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(settlementService.getMySettlements(userId, cursor, size));
    }
}
