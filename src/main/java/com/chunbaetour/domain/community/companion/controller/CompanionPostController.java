package com.chunbaetour.domain.community.companion.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.community.companion.dto.CompanionPostCreateRequest;
import com.chunbaetour.domain.community.companion.dto.CompanionPostCreateResponse;
import com.chunbaetour.domain.community.companion.dto.CompanionPostUpdateResponse;
import com.chunbaetour.domain.community.companion.dto.CompanionPostGetListResponse;
import com.chunbaetour.domain.community.companion.dto.CompanionPostGetOneResponse;
import com.chunbaetour.domain.community.companion.dto.CompanionPostUpdateRequest;
import com.chunbaetour.domain.community.companion.service.CompanionPostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "동행 게시판", description = "동행 모집 게시글 CRUD (/api/v1/community/posts/companions/**)")
@RestController
@RequestMapping("/api/v1/community/posts/companions")
@RequiredArgsConstructor
@Validated
public class CompanionPostController {

    private final CompanionPostService postService;

    @Operation(summary = "동행 게시글 작성")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CompanionPostCreateResponse> create(
            @AuthenticationPrincipal Long accountId,
            @Valid @RequestBody CompanionPostCreateRequest request) {
        return ApiResponse.success(postService.create(accountId, request));
    }

    @Operation(summary = "동행 게시글 목록 조회")
    @GetMapping
    public ApiResponse<CursorPageResponse<CompanionPostGetListResponse>> findAll(
            @RequestParam(required = false) String region,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate meetingDate,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return ApiResponse.success(postService.findAll(region, meetingDate, cursor, size));
    }

    @Operation(summary = "동행 게시글 단건 조회")
    @GetMapping("/{postId}")
    public ApiResponse<CompanionPostGetOneResponse> findById(@Positive @PathVariable Long postId) {
        return ApiResponse.success(postService.findById(postId));
    }

    @Operation(summary = "동행 게시글 수정")
    @PutMapping("/{postId}")
    public ApiResponse<CompanionPostUpdateResponse> update(
            @AuthenticationPrincipal Long accountId,
            @Positive @PathVariable Long postId,
            @Valid @RequestBody CompanionPostUpdateRequest request) {
        return ApiResponse.success(postService.update(accountId, postId, request));
    }

    @Operation(summary = "동행 게시글 삭제")
    @DeleteMapping("/{postId}")
    public ApiResponse<String> delete(
            @AuthenticationPrincipal Long accountId,
            @Positive @PathVariable Long postId) {
        postService.delete(accountId, postId);
        return ApiResponse.success("게시글이 삭제되었습니다.");
    }
}
