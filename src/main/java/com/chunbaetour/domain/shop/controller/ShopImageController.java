package com.chunbaetour.domain.shop.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.shop.dto.response.ShopImageResponse;
import com.chunbaetour.domain.shop.service.ShopImageService;
import io.swagger.v3.oas.annotations.Hidden;
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
 * /api/v1/merchants/** 경로는 SecurityConfig에서 MERCHANT 권한 필수.
 * S3 설정 완료 전까지 호출 시 COMMON_007(외부 서비스 오류) 반환.
 */
@Tag(name = "가게 이미지 (MERCHANT)", description = "가게 사진 업로드 (/api/v1/merchants/me/shops/{shopId}/images)")
@RestController
@RequestMapping("/api/v1/merchants/me/shops/{shopId}/images")
@RequiredArgsConstructor
public class ShopImageController {

    private final ShopImageService shopImageService;

    @Hidden // S3 미설정 stub — 클라이언트에 노출하지 않음. S3 통합 완료 후 제거
    @Operation(summary = "가게 사진 업로드")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ShopImageResponse> uploadImage(
            @AuthenticationPrincipal Long userId,
            @PathVariable @Positive Long shopId,
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.success(shopImageService.uploadImage(userId, shopId, file));
    }
}
