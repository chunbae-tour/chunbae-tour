package com.chunbaetour.domain.auth.profileimage;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 프로필 이미지 업로드 API (KAN-320).
 *
 * <p>{@code POST /api/v1/users/me/profile-image} (multipart, USER) — 이미지 1장 업로드 → 본인 프로필에 즉시 반영(1단계).
 * 부모(유저)가 항상 존재하므로 업로드 즉시 {@code Account.profileImageUrl}을 세팅한다(채팅·가게 1단계 패턴과 동일).
 */
@Tag(name = "내 프로필 이미지 (USER)", description = "프로필 이미지 업로드 (/api/v1/users/me/profile-image)")
@RestController
@RequestMapping("/api/v1/users/me/profile-image")
@RequiredArgsConstructor
public class ProfileImageController {

    private final ProfileImageService profileImageService;

    @Operation(summary = "프로필 이미지 업로드",
            description = "이미지 1장을 업로드해 본인 프로필에 즉시 반영한다(1단계). JPEG/PNG/WebP만 허용(magic-byte 검증), 5MB 이하. "
                    + "응답 previewUrl은 presigned GET URL. USER 권한 필수.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProfileImageUploadResponse> upload(
            @AuthenticationPrincipal Long userId,
            @RequestParam("file") MultipartFile file) {
        // /api/v1/users/** = hasRole(USER)라 정상 흐름엔 null 불가하나 방어
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
        return ApiResponse.success(profileImageService.upload(userId, file));
    }
}
