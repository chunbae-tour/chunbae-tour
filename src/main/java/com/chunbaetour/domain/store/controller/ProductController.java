package com.chunbaetour.domain.store.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.store.dto.response.ProductDetailResponse;
import com.chunbaetour.domain.store.dto.response.ProductSummaryResponse;
import com.chunbaetour.domain.store.service.ProductService;
import com.chunbaetour.domain.store.type.ProductCategory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 스토어 상품 조회 API (STORY-16) */
@Tag(name = "스토어 상품", description = "상품 목록·상세 조회 (/api/v1/store/products/**)")
@RestController
@RequestMapping("/api/v1/store/products")
@RequiredArgsConstructor
@Validated
public class ProductController {

    private final ProductService productService;

    @SecurityRequirements
    @Operation(summary = "상품 목록 조회")
    @GetMapping
    public ApiResponse<CursorPageResponse<ProductSummaryResponse>> getProducts(
            @RequestParam(required = false) ProductCategory category,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(productService.getProducts(category, cursor, size));
    }

    @SecurityRequirements
    @Operation(summary = "상품 상세 조회")
    @GetMapping("/{productId}")
    public ApiResponse<ProductDetailResponse> getProduct(@PathVariable Long productId) {
        return ApiResponse.success(productService.getProduct(productId));
    }
}
