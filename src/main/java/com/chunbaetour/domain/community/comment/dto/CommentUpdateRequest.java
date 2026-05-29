package com.chunbaetour.domain.community.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentUpdateRequest(
        @NotBlank @Size(max = 1000, message = "댓글은 최대 1000자까지 입력 가능합니다.") String content
) {}
