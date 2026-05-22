package com.chunbaetour.domain.community.comment.dto;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.community.comment.entity.Comment;
import com.chunbaetour.domain.community.common.WriterInfo;
import java.time.LocalDateTime;

public record CommentGetListResponse(
        Long commentId,
        String content,
        WriterInfo writer,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static CommentGetListResponse of(Comment comment, Account author) {
        // 댓글은 companionScore 미노출 — WriterInfo.from() 대신 직접 생성 (null 안전)
        WriterInfo writer = author != null
                ? new WriterInfo(author.getId(), author.getNickname(), author.getProfileImageUrl(), null)
                : new WriterInfo(null, "탈퇴한 사용자", null, null);
        return new CommentGetListResponse(
                comment.getId(),
                comment.getContent(),
                writer,
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }
}
