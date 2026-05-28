package com.chunbaetour.domain.store.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.store.dto.request.StorePurchaseRequest;
import com.chunbaetour.domain.store.dto.response.StoreOrderResponse;
import com.chunbaetour.domain.store.service.StorePurchaseService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 스토어 주문 API (STORY-17) */
@RestController
@RequestMapping("/api/v1/store/orders")
@RequiredArgsConstructor
@Validated
public class StoreOrderController {

    private final StorePurchaseService storePurchaseService;

    /** 상품 구매 — 3단계 동시성 제어 (Redis 선점 + 분산 락 + 비관적 락) */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<StoreOrderResponse> purchase(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid StorePurchaseRequest request) {
        return ApiResponse.success(storePurchaseService.purchase(userId, request));
    }

    /** 내 주문 내역 조회 — cursor 페이징 */
    @GetMapping
    public ApiResponse<CursorPageResponse<StoreOrderResponse>> getMyOrders(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(storePurchaseService.getMyOrders(userId, cursor, size));
    }
}
