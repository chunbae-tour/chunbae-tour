package com.chunbaetour.domain.community.companion.dto;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.community.common.WriterInfo;
import com.chunbaetour.domain.community.companion.entity.CompanionPost;
import com.chunbaetour.domain.community.companion.entity.CompanionPostStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CompanionPostGetOneResponse(
        Long postId,
        String title,
        String content,
        Long placeId,
        String placeName,
        String region,
        LocalDate meetingDate,
        int maxMembers,
        int currentMembers,
        CompanionPostStatus status,
        WriterInfo writer,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static CompanionPostGetOneResponse of(CompanionPost post, Account author) {
        WriterInfo writer = author != null
                ? new WriterInfo(author.getId(), author.getNickname(), author.getProfileImageUrl(), (double) author.getCompanionScore())
                : new WriterInfo(null, "탈퇴한 사용자", null, null);
        return new CompanionPostGetOneResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getPlaceId(),
                post.getPlaceName(),
                post.getRegion(),
                post.getMeetingDate(),
                post.getMaxMembers(),
                post.getCurrentMembers(),
                post.getStatus(),
                writer,
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}
