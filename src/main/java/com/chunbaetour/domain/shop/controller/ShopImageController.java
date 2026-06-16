package com.chunbaetour.domain.shop.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.shop.dto.response.ShopImageResponse;
import com.chunbaetour.domain.shop.service.ShopImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 가게 사진 업로드 API.
 * /api/v1/merchants/** 경로는 SecurityConfig에서 MERCHANT 권한 필수이며,
 * 본인 소유 가게(shopId) 검증으로 타 가게 업로드(IDOR)를 차단한다.
 */
@Tag(name = "가게 이미지 (MERCHANT)", description = "가게 사진 업로드 (/api/v1/merchants/me/shops/{shopId}/images)")
@RestController
@RequestMapping("/api/v1/merchants/me/shops/{shopId}/images")
@RequiredArgsConstructor
public class ShopImageController {

    private final ShopImageService shopImageService;

    @Operation(summary = "가게 사진 업로드",
            description = "본인 소유 가게에 이미지 1장을 업로드한다. JPEG/PNG/WebP만 허용(magic-byte 검증). "
                    + "응답의 키는 조회 시 presigned GET URL로 변환되어 제공된다. MERCHANT 권한 + 본인 가게 검증 필수.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ShopImageResponse> uploadImage(
            @AuthenticationPrincipal Long userId,
            @PathVariable @Positive Long shopId,
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.success(shopImageService.uploadImage(userId, shopId, file));
    }
}
