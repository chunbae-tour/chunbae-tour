package com.chunbaetour.domain.community.free.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.community.free.dto.PostImageUploadResponse;
import com.chunbaetour.domain.community.free.service.PostImageService;
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
 * 자유게시판 이미지 업로드 API (KAN-317).
 *
 * <p>{@code POST /api/v1/community/posts/free/images} — 이미지 1장 업로드 → 객체 키 반환(USER 권한).
 * 글 작성 전 화면에서 첨부하므로 업로드 시점엔 postId가 없다 → 키만 받아 두고, 글 작성/수정 요청의 {@code imageUrls}에 담아 저장한다.
 */
@Tag(name = "자유 게시판 이미지", description = "자유게시판 이미지 업로드 (/api/v1/community/posts/free/images)")
@RestController
@RequestMapping("/api/v1/community/posts/free/images")
@RequiredArgsConstructor
public class PostImageController {

    private final PostImageService postImageService;

    @Operation(summary = "자유게시판 이미지 업로드",
            description = "이미지 1장을 업로드하고 S3 객체 키를 반환한다. JPEG/PNG/WebP만 허용(magic-byte 검증), 장당 10MB 이하. "
                    + "반환 키를 글 작성/수정 요청의 imageUrls에 담아 저장한다(최대 5장). USER 권한 필수.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PostImageUploadResponse> upload(
            @AuthenticationPrincipal Long userId,
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.success(postImageService.upload(userId, file));
    }
}
