package com.chunbaetour.domain.community.free.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.community.common.CursorPage;
import com.chunbaetour.domain.community.free.dto.FreePostCreateRequest;
import com.chunbaetour.domain.community.free.dto.FreePostGetOneResponse;
import com.chunbaetour.domain.community.free.dto.FreePostGetListResponse;
import com.chunbaetour.domain.community.free.dto.FreePostUpdateRequest;
import com.chunbaetour.domain.community.free.service.FreePostService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

@RestController
@RequestMapping("/api/v1/community/posts/free")
@RequiredArgsConstructor
public class FreePostController {

    private final FreePostService postService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FreePostGetOneResponse> create(
            @AuthenticationPrincipal Long accountId,
            @Valid @RequestBody FreePostCreateRequest request) {
        return ApiResponse.success(postService.create(accountId, request));
    }

    @GetMapping
    public ApiResponse<CursorPage<FreePostGetListResponse>> findAll(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return ApiResponse.success(postService.findAll(cursor, size));
    }

    @GetMapping("/{postId}")
    public ApiResponse<FreePostGetOneResponse> findById(@PathVariable Long postId) {
        return ApiResponse.success(postService.findById(postId));
    }

    @PatchMapping("/{postId}")
    public ApiResponse<FreePostGetOneResponse> update(
            @AuthenticationPrincipal Long accountId,
            @PathVariable Long postId,
            @Valid @RequestBody FreePostUpdateRequest request) {
        return ApiResponse.success(postService.update(accountId, postId, request));
    }

    @DeleteMapping("/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal Long accountId,
            @PathVariable Long postId) {
        postService.delete(accountId, postId);
    }
}
