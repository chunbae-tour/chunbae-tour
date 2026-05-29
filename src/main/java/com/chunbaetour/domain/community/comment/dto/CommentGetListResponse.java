package com.chunbaetour.domain.community.comment.dto;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.community.comment.entity.Comment;
import com.chunbaetour.domain.community.common.WriterInfo;
import java.time.LocalDateTime;

public record CommentGetListResponse(
        Long commentId,
        Long parentCommentId,
        String content,
        WriterInfo writer,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static CommentGetListResponse of(Comment comment, Account author) {
        return new CommentGetListResponse(
                comment.getId(),
                comment.getParentCommentId(),
                comment.getContent(),
                WriterInfo.fromComment(author),
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }
}
