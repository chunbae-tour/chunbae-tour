package com.chunbaetour.domain.community.comment.dto;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.community.comment.entity.Comment;
import com.chunbaetour.domain.community.comment.entity.CommentStatus;
import com.chunbaetour.domain.community.common.WriterInfo;
import java.time.LocalDateTime;

public record CommentGetListResponse(
        Long commentId,
        Long parentCommentId,
        String content,
        WriterInfo writer,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long replyCount,
        boolean deleted
) {
    // 루트 댓글용 — 삭제 시 content·writer 마스킹, replyCount 포함
    public static CommentGetListResponse ofRoot(Comment comment, Account author, long replyCount) {
        boolean deleted = comment.getStatus() == CommentStatus.DELETED;
        return new CommentGetListResponse(
                comment.getId(),
                null,
                deleted ? "(삭제된 댓글)" : comment.getContent(),
                deleted ? null : WriterInfo.fromComment(author),
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                replyCount,
                deleted
        );
    }

    // 대댓글용 — replyCount 불필요 (1단계 제한)
    public static CommentGetListResponse ofReply(Comment comment, Account author) {
        return new CommentGetListResponse(
                comment.getId(),
                comment.getParentCommentId(),
                comment.getContent(),
                WriterInfo.fromComment(author),
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                0,
                false
        );
    }
}
