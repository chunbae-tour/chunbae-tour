package com.chunbaetour.domain.place.dto.response;

import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.UserLike;
import com.chunbaetour.domain.place.type.PlaceCategory;
import java.time.LocalDateTime;

/**
 * 마이페이지 연동용 (PHASE 3-3)
 * 사용자가 찜한 관광지 목록 조회 응답 DTO
 */
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
    public static UserLikedPlaceResponse from(UserLike userLike) {
        Place place = userLike.getPlace();
        return new UserLikedPlaceResponse(
                place.getId(),
                place.getName(),
                place.getCategory(),
                place.getAddress(),
                place.getThumbnailUrl(),
                place.getRating(),
                place.getReviewCount(),
                place.getLikeCount(),
                userLike.getCreatedAt()
        );
    }
}
