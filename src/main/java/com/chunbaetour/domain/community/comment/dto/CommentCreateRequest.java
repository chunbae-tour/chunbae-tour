package com.chunbaetour.domain.community.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CommentCreateRequest(
        @NotBlank @Size(max = 1000, message = "댓글은 최대 1000자까지 입력 가능합니다.") String content,
        @Positive(message = "parentCommentId는 1 이상의 값이어야 합니다.") Long parentCommentId
) {
}
