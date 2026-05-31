package com.chunbaetour.domain.community.comment.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.community.comment.dto.CommentCreateRequest;
import com.chunbaetour.domain.community.comment.dto.CommentCreateResponse;
import com.chunbaetour.domain.community.comment.dto.CommentGetListResponse;
import com.chunbaetour.domain.community.comment.dto.CommentUpdateRequest;
import com.chunbaetour.domain.community.common.PostType;
import com.chunbaetour.domain.community.comment.service.CommentService;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

// GET(댓글 목록·대댓글)은 비인증 허용 — SecurityConfig에서 GET /api/v1/community/** permitAll 처리
// POST·PATCH·DELETE는 USER·ADMIN 인증 필요
@Tag(name = "댓글", description = "게시글 댓글 작성·목록 조회·수정·삭제·대댓글 더보기 (/api/v1/community/posts/{postType}/{postId}/comments/**)")
@Validated
@RestController
@RequestMapping("/api/v1/community/posts/{postType}/{postId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @Operation(summary = "댓글 작성")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CommentCreateResponse> create(
            @AuthenticationPrincipal Long accountId,
            @PathVariable String postType,
            @PathVariable Long postId,
            @Valid @RequestBody CommentCreateRequest request) {
        return ApiResponse.success(commentService.create(accountId, postId, PostType.from(postType), request));
    }

    @Operation(summary = "댓글 목록 조회 (루트 댓글 cursor 페이징)")
    @GetMapping
    public ApiResponse<CursorPageResponse<CommentGetListResponse>> findAll(
            @PathVariable String postType,
            @PathVariable Long postId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return ApiResponse.success(commentService.findAll(postId, PostType.from(postType), cursor, size));
    }

    @Operation(summary = "대댓글 더보기 — 특정 루트 댓글의 대댓글 전체 조회")
    @GetMapping("/{commentId}/replies")
    public ApiResponse<List<CommentGetListResponse>> findReplies(
            @PathVariable String postType,
            @PathVariable Long postId,
            @PathVariable Long commentId) {
        return ApiResponse.success(commentService.findReplies(postId, PostType.from(postType), commentId));
    }

    @Operation(summary = "댓글 수정")
    @PatchMapping("/{commentId}")
    public ApiResponse<Void> update(
            @AuthenticationPrincipal Long accountId,
            @PathVariable String postType,
            @PathVariable Long postId,
            @PathVariable Long commentId,
            @Valid @RequestBody CommentUpdateRequest request) {
        commentService.update(accountId, postId, PostType.from(postType), commentId, request);
        return ApiResponse.success();
    }

    @Operation(summary = "댓글 삭제")
    @DeleteMapping("/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal Long accountId,
            @PathVariable String postType,
            @PathVariable Long postId,
            @PathVariable Long commentId) {
        commentService.delete(accountId, postId, PostType.from(postType), commentId);
    }
}
