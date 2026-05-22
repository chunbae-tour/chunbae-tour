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
        // 탈퇴한 작성자는 null — WriterInfo 직접 생성으로 NPE 방지
        WriterInfo writer = author != null
                ? new WriterInfo(author.getId(), author.getNickname(), author.getProfileImageUrl(), null)
                : new WriterInfo(null, "탈퇴한 사용자", null, null);
        return new CommentGetListResponse(
                comment.getId(),
                comment.getParentCommentId(),
                comment.getContent(),
                writer,
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }
}
