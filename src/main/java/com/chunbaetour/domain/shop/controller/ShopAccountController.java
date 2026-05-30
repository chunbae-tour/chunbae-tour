package com.chunbaetour.domain.shop.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.shop.dto.request.ShopAccountRequest;
import com.chunbaetour.domain.shop.dto.response.ShopAccountResponse;
import com.chunbaetour.domain.shop.service.ShopAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 정산 계좌 등록/변경 API.
 * /api/v1/merchants/** 경로는 SecurityConfig에서 MERCHANT 권한 필수.
 */
@Tag(name = "정산 계좌 (MERCHANT)", description = "가게 정산 계좌 등록/변경 (/api/v1/merchants/me/shops/{shopId}/account)")
@Validated
@RestController
@RequestMapping("/api/v1/merchants/me/shops/{shopId}/account")
@RequiredArgsConstructor
public class ShopAccountController {

    private final ShopAccountService shopAccountService;

    @Operation(summary = "정산 계좌 등록/변경")
    @PutMapping
    public ApiResponse<ShopAccountResponse> updateAccount(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long shopId,
            @Valid @RequestBody ShopAccountRequest request) {
        return ApiResponse.success(shopAccountService.updateAccount(userId, shopId, request));
    }
}
