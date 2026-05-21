package com.chunbaetour.domain.community.companion.dto;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.community.common.WriterInfo;
import com.chunbaetour.domain.community.companion.entity.CompanionPost;
import com.chunbaetour.domain.community.companion.entity.CompanionPostStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CompanionPostCreateResponse(
        Long postId,
        String title,
        String content,
        Long placeId,
        String placeName,
        LocalDate meetingDate,
        int maxMembers,
        int currentMembers,
        CompanionPostStatus status,
        WriterInfo writer,
        LocalDateTime createdAt
) {
    public static CompanionPostCreateResponse of(CompanionPost post, Account author) {
        return new CompanionPostCreateResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getPlaceId(),
                post.getPlaceName(),
                post.getMeetingDate(),
                post.getMaxMembers(),
                post.getCurrentMembers(),
                post.getStatus(),
                WriterInfo.from(author),
                post.getCreatedAt()
        );
    }
}
