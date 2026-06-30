package com.chunbaetour.domain.companion.review.dto.response;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.profileimage.ProfileImageDisplaySupport;
import com.chunbaetour.domain.companion.review.entity.CompanionReview;
import java.time.LocalDateTime;

public record CompanionReviewResponse(
        Long reviewId,
        Long reviewerId,
        String reviewerNickname,
        String reviewerProfileImageUrl,
        int score,
        String content,
        LocalDateTime createdAt
) {
    // reviewer null: 탈퇴한 사용자 fallback
    public static CompanionReviewResponse of(CompanionReview review, Account reviewer) {
        String nickname = reviewer != null ? reviewer.getNickname() : "탈퇴한 사용자";
        Long reviewerId = reviewer != null ? reviewer.getId() : null;
        String profileImageUrl = reviewer != null
                ? ProfileImageDisplaySupport.toDisplayUrl(reviewer.getProfileImageUrl()) : null;
        return new CompanionReviewResponse(
                review.getId(),
                reviewerId,
                nickname,
                profileImageUrl,
                review.getScore(),
                review.getContent(),
                review.getCreatedAt()
        );
    }
}
