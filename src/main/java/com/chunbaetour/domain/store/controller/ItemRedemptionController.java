package com.chunbaetour.domain.store.controller;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.store.dto.request.UserItemUseRequest;
import com.chunbaetour.domain.store.dto.response.UserItemUseResponse;
import com.chunbaetour.domain.store.service.UserItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "스토어 아이템 사용 처리",
        description = "검증자가 사용자 아이템 QR을 확인해 사용 처리합니다. MVP에서는 MERCHANT 권한으로 검증합니다. (/api/v1/merchants/me/shop/items/**)"
)
@RestController
@RequestMapping("/api/v1/merchants/me/shop/items")
@RequiredArgsConstructor
public class ItemRedemptionController {

    private final UserItemService userItemService;

    @Operation(summary = "사용자 아이템 QR 사용 처리")
    @PostMapping("/use")
    public ApiResponse<UserItemUseResponse> useItem(
            @AuthenticationPrincipal Long verifierUserId,
            @Valid @RequestBody UserItemUseRequest request) {
        requireAuthenticated(verifierUserId);
        return ApiResponse.success(userItemService.useByQr(verifierUserId, request.shopId(), request.token()));
    }

    private static void requireAuthenticated(Long verifierUserId) {
        if (verifierUserId == null) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
    }
}
