package com.chunbaetour.domain.store.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.store.dto.request.AdminProductCreateRequest;
import com.chunbaetour.domain.store.dto.request.AdminProductUpdateRequest;
import com.chunbaetour.domain.store.dto.response.ProductDetailResponse;
import com.chunbaetour.domain.store.dto.response.ProductSummaryResponse;
import com.chunbaetour.domain.store.service.AdminProductService;
import com.chunbaetour.domain.store.type.ProductStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 상품 CRUD API.
 * /api/v1/admin/** 경로는 SecurityConfig에서 ADMIN 권한 필수.
 */
@Tag(name = "관리자 상품 관리 (ADMIN)", description = "상품 등록·수정·삭제 (/api/v1/admin/store/products/**)")
@Validated
@RestController
@RequestMapping("/api/v1/admin/store/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final AdminProductService adminProductService;

    @Operation(summary = "상품 목록 조회 (HIDDEN 포함)",
            description = "공개 목록과 달리 숨김(HIDDEN) 상품까지 전체 status 노출. status 미지정 시 전체.")
    @GetMapping
    public ApiResponse<CursorPageResponse<ProductSummaryResponse>> getProducts(
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(adminProductService.getProducts(status, category, cursor, size));
    }

    @Operation(summary = "상품 등록")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductDetailResponse> createProduct(
            @Valid @RequestBody AdminProductCreateRequest request) {
        return ApiResponse.success(adminProductService.createProduct(request));
    }

    @Operation(summary = "상품 수정")
    @PatchMapping("/{productId}")
    public ApiResponse<ProductDetailResponse> updateProduct(
            @PathVariable @Positive Long productId,
            @Valid @RequestBody AdminProductUpdateRequest request) {
        return ApiResponse.success(adminProductService.updateProduct(productId, request));
    }

    @Operation(summary = "상품 숨김 처리 (soft delete) — status=HIDDEN으로 전환, 실제 삭제 아님")
    @DeleteMapping("/{productId}")
    public ApiResponse<ProductDetailResponse> deleteProduct(@PathVariable @Positive Long productId) {
        return ApiResponse.success(adminProductService.deleteProduct(productId));
    }
}
