package com.chunbaetour.domain.community.comment.dto;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.community.comment.entity.Comment;
import com.chunbaetour.domain.community.common.WriterInfo;
import java.time.LocalDateTime;

public record CommentCreateResponse(
        Long commentId,
        Long parentCommentId,
        String content,
        WriterInfo writer,
        LocalDateTime createdAt
) {
    public static CommentCreateResponse of(Comment comment, Account author) {
        WriterInfo writer = new WriterInfo(
                author.getId(),
                author.getNickname(),
                author.getProfileImageUrl(),
                null
        );
        return new CommentCreateResponse(
                comment.getId(),
                comment.getParentCommentId(),
                comment.getContent(),
                writer,
                comment.getCreatedAt()
        );
    }
}
