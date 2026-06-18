package com.chunbaetour.domain.community.companion.dto;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.community.common.WriterInfo;
import com.chunbaetour.domain.community.companion.entity.CompanionPost;
import com.chunbaetour.domain.community.companion.entity.CompanionPostStatus;
import com.chunbaetour.domain.community.companion.entity.CompanionTargetType;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CompanionPostUpdateResponse(
        Long postId,
        String title,
        String content,
        CompanionTargetType targetType,
        Long targetId,
        String targetName,
        String region,
        LocalDate meetingDate,
        int maxMembers,
        int currentMembers,
        CompanionPostStatus status,
        WriterInfo writer,
        LocalDateTime updatedAt
) {
    public static CompanionPostUpdateResponse of(CompanionPost post, Account author) {
        return new CompanionPostUpdateResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getTargetType(),
                post.getTargetId(),
                post.getTargetName(),
                post.getRegion(),
                post.getMeetingDate(),
                post.getMaxMembers(),
                post.getCurrentMembers(),
                post.getStatus(),
                WriterInfo.from(author),
                post.getUpdatedAt()
        );
    }
}
