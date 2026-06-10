package com.chunbaetour.domain.companionreview.dto.response;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.companionreview.entity.CompanionReview;
import java.time.LocalDateTime;

public record CompanionReviewResponse(
        Long reviewId,
        String reviewerNickname,
        int score,
        String content,
        LocalDateTime createdAt
) {
    // reviewer null: 탈퇴한 사용자 fallback
    public static CompanionReviewResponse of(CompanionReview review, Account reviewer) {
        String nickname = reviewer != null ? reviewer.getNickname() : "탈퇴한 사용자";
        return new CompanionReviewResponse(
                review.getId(),
                nickname,
                review.getScore(),
                review.getContent(),
                review.getCreatedAt()
        );
    }
}
