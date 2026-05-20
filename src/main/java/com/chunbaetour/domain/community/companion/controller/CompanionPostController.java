package com.chunbaetour.domain.community.companion.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.community.common.CursorPage;
import com.chunbaetour.domain.community.companion.dto.CompanionPostCreateRequest;
import com.chunbaetour.domain.community.companion.dto.CompanionPostCreateResponse;
import com.chunbaetour.domain.community.companion.dto.CompanionPostGetListResponse;
import com.chunbaetour.domain.community.companion.dto.CompanionPostGetOneResponse;
import com.chunbaetour.domain.community.companion.dto.CompanionPostUpdateRequest;
import com.chunbaetour.domain.community.companion.service.CompanionPostService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/v1/community/posts/companions")
@RequiredArgsConstructor
public class CompanionPostController {

    private final CompanionPostService postService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CompanionPostCreateResponse> create(
            @AuthenticationPrincipal Long accountId,
            @Valid @RequestBody CompanionPostCreateRequest request) {
        return ApiResponse.success(postService.create(accountId, request));
    }

    @GetMapping
    public ApiResponse<CursorPage<CompanionPostGetListResponse>> findAll(
            @RequestParam(required = false) String region,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate meetingDate,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(postService.findAll(region, meetingDate, cursor, size));
    }

    @GetMapping("/{postId}")
    public ApiResponse<CompanionPostGetOneResponse> findById(@PathVariable Long postId) {
        return ApiResponse.success(postService.findById(postId));
    }

    @PatchMapping("/{postId}")
    public ApiResponse<CompanionPostCreateResponse> update(
            @AuthenticationPrincipal Long accountId,
            @PathVariable Long postId,
            @Valid @RequestBody CompanionPostUpdateRequest request) {
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
