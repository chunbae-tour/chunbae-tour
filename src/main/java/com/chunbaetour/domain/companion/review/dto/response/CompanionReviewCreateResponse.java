package com.chunbaetour.domain.companion.review.dto.response;

import com.chunbaetour.domain.companion.review.entity.CompanionReview;
import java.time.LocalDateTime;

public record CompanionReviewCreateResponse(
        Long reviewId,
        Long chatRoomId,
        Long writerUserId,
        Long targetUserId,
        int score,
        String content,
        LocalDateTime createdAt
) {
    // CompanionReview 엔티티로부터 응답 생성
    public static CompanionReviewCreateResponse from(CompanionReview review) {
        return new CompanionReviewCreateResponse(
                review.getId(),
                review.getChatRoomId(),
                review.getReviewerId(),
                review.getTargetUserId(),
                review.getScore(),
                review.getContent(),
                review.getCreatedAt()
        );
    }
}
