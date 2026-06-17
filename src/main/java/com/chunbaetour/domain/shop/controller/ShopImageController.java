package com.chunbaetour.domain.shop.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.shop.dto.response.ShopImageItemResponse;
import com.chunbaetour.domain.shop.service.ShopImageService;
import com.chunbaetour.domain.shop.type.ShopImageType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 가게 사진 API (KAN-188 E10 → KAN-315 ADR-003).
 *
 * <p>POST   /api/v1/merchants/me/shops/{shopId}/images?type=  — 업로드(행 즉시 생성, 1단계화)
 * <br>GET    /api/v1/merchants/me/shops/{shopId}/images        — 목록 조회(presign 변환)
 * <br>DELETE /api/v1/merchants/me/shops/{shopId}/images/{imageId} — 삭제(행 + S3 객체)
 *
 * <p>{@code /api/v1/merchants/**} 경로는 SecurityConfig에서 MERCHANT 권한 필수이며, 본인 소유 가게(shopId)
 * 검증으로 타 가게 접근(IDOR)을 차단한다.
 */
@Tag(name = "가게 이미지 (MERCHANT)", description = "가게 사진 업로드·조회·삭제 (/api/v1/merchants/me/shops/{shopId}/images/**)")
@RestController
@RequestMapping("/api/v1/merchants/me/shops/{shopId}/images")
@RequiredArgsConstructor
@Validated
public class ShopImageController {

    private final ShopImageService shopImageService;

    @Operation(summary = "가게 사진 업로드",
            description = "본인 소유 가게에 이미지 1장을 업로드하고 즉시 사진 행을 생성한다(자동반영). type=PROFILE(대표, 가게당 1장 교체)"
                    + " / GALLERY(소개, 다중). JPEG/PNG/WebP만 허용(magic-byte 검증). 응답 url은 presigned GET URL이다.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ShopImageItemResponse> uploadImage(
            @AuthenticationPrincipal Long userId,
            @PathVariable @Positive Long shopId,
            @RequestParam("type") ShopImageType type,
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.success(shopImageService.uploadImage(userId, shopId, type, file));
    }

    @Operation(summary = "가게 사진 목록 조회",
            description = "본인 가게 사진을 PROFILE 우선 + 갤러리 정렬순으로 조회한다. 각 url은 presigned GET URL. 상태 무관 허용.")
    @GetMapping
    public ApiResponse<List<ShopImageItemResponse>> getImages(
            @AuthenticationPrincipal Long userId,
            @PathVariable @Positive Long shopId) {
        return ApiResponse.success(shopImageService.getImages(userId, shopId));
    }

    @Operation(summary = "가게 사진 삭제",
            description = "본인 가게 사진을 삭제한다(행 제거 + S3 객체 삭제). ACTIVE 가게만 허용. 타 가게 이미지는 404.")
    @DeleteMapping("/{imageId}")
    public ApiResponse<Void> deleteImage(
            @AuthenticationPrincipal Long userId,
            @PathVariable @Positive Long shopId,
            @PathVariable @Positive Long imageId) {
        shopImageService.deleteImage(userId, shopId, imageId);
        return ApiResponse.success();
    }
}
