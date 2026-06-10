package com.chunbaetour.domain.place.dto.response;

import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.type.PlaceCategory;
import java.time.LocalDateTime;

import lombok.Builder;

/**
 * 마이페이지 연동용 (PHASE 3-3)
 * 사용자가 찜한 관광지 목록 조회 응답 DTO
 */
@Builder
public record UserLikedPlaceResponse(
        Long placeId,
        String name,
        PlaceCategory category,
        String address,
        String thumbnailUrl,
        float rating,
        int reviewCount,
        int likeCount,
        LocalDateTime likedAt
) {
    public static UserLikedPlaceResponse from(Place place, LocalDateTime likedAt) {
        return UserLikedPlaceResponse.builder()
                .placeId(place.getId())
                .name(place.getName())
                .category(place.getCategory())
                .address(place.getAddress())
                .thumbnailUrl(place.getThumbnailUrl())
                .rating(place.getDisplayRating())
                .reviewCount(place.getReviewCount())
                .likeCount(place.getLikeCount())
                .likedAt(likedAt)
                .build();
    }
}
