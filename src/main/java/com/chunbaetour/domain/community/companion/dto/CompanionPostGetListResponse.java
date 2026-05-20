package com.chunbaetour.domain.community.companion.dto;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.community.common.WriterInfo;
import com.chunbaetour.domain.community.companion.entity.CompanionPost;
import com.chunbaetour.domain.community.companion.entity.CompanionPostStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CompanionPostGetListResponse(
        Long postId,
        String title,
        String placeName,
        LocalDate meetingDate,
        int maxMembers,
        int currentMembers,
        CompanionPostStatus status,
        WriterInfo writer,
        int commentCount,
        LocalDateTime createdAt
) {
    public static CompanionPostGetListResponse of(CompanionPost post, Account author) {
        WriterInfo writer = new WriterInfo(
                author.getId(),
                author.getNickname(),
                author.getProfileImageUrl(),
                (double) author.getCompanionScore()
        );
        return new CompanionPostGetListResponse(
                post.getId(),
                post.getTitle(),
                post.getPlaceName(),
                post.getMeetingDate(),
                post.getMaxMembers(),
                post.getCurrentMembers(),
                post.getStatus(),
                writer,
                0,
                post.getCreatedAt()
        );
    }
}
