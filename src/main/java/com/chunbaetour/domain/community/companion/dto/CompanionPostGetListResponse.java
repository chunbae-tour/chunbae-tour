package com.chunbaetour.domain.community.companion.dto;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.community.common.WriterInfo;
import com.chunbaetour.domain.community.companion.entity.CompanionPost;
import com.chunbaetour.domain.community.companion.entity.CompanionPostStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CompanionPostGetListResponse(
        Long postId,
        Long chatRoomId,
        String title,
        String placeName,
        String region,
        LocalDate meetingDate,
        int maxMembers,
        int currentMembers,
        CompanionPostStatus status,
        WriterInfo writer,
        LocalDateTime createdAt
) {
    public static CompanionPostGetListResponse of(CompanionPost post, Account author, Long chatRoomId) {
        return new CompanionPostGetListResponse(
                post.getId(),
                chatRoomId,
                post.getTitle(),
                post.getPlaceName(),
                post.getRegion(),
                post.getMeetingDate(),
                post.getMaxMembers(),
                post.getCurrentMembers(),
                post.getStatus(),
                WriterInfo.from(author),
                post.getCreatedAt()
        );
    }
}
