package com.chunbaetour.domain.like.dto.response;

import com.chunbaetour.domain.like.type.LikeTargetType;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * My-page liked target response shared by place, traditional market, and festival.
 *
 * <p>Only card-level fields common enough for a liked-list UI are exposed here. Domain-specific
 * values that do not exist for every target type stay nullable rather than forcing separate
 * response schemas per type.
 */
public record UserLikedTargetResponse(
        Long targetId,
        LikeTargetType type,
        String name,
        String category,
        String address,
        String imageUrl,
        Float rating,
        Integer reviewCount,
        Integer likeCount,
        String region,
        LocalDate startDate,
        LocalDate endDate,
        LocalDateTime likedAt
) {
}
