package com.chunbaetour.domain.community.companion.dto;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.community.common.WriterInfo;
import com.chunbaetour.domain.community.companion.entity.CompanionPost;
import com.chunbaetour.domain.community.companion.entity.CompanionPostStatus;
import com.chunbaetour.domain.community.companion.entity.CompanionTargetType;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CompanionPostCreateResponse(
        Long postId,
        String title,
        String content,
        CompanionTargetType targetType,
        Long targetId,
        String targetName,
        LocalDate meetingDate,
        int maxMembers,
        int currentMembers,
        CompanionPostStatus status,
        WriterInfo writer,
        long viewCount,
        long commentCount,
        LocalDateTime createdAt
) {
    public static CompanionPostCreateResponse of(CompanionPost post, Account author) {
        return new CompanionPostCreateResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getTargetType(),
                post.getTargetId(),
                post.getTargetName(),
                post.getMeetingDate(),
                post.getMaxMembers(),
                post.getCurrentMembers(),
                post.getStatus(),
                WriterInfo.from(author),
                post.getViewCount(),
                0L,
                post.getCreatedAt()
        );
    }
}
