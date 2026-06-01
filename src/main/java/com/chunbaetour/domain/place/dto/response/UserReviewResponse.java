package com.chunbaetour.domain.place.dto.response;

import com.chunbaetour.domain.place.Place;
import com.chunbaetour.domain.place.PlaceReview;
import com.chunbaetour.domain.place.type.PlaceCategory;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import lombok.Builder;

/**
 * 마이페이지 연동용 (PHASE 4-6)
 * 사용자가 작성한 관광지 리뷰 목록 조회 응답 DTO
 */
@Builder
public record UserReviewResponse(
        Long reviewId,
        Long placeId,
        String placeName,
        PlaceCategory category,
        String thumbnailUrl,
        int rating,
        String content,
        List<String> imageUrls,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    /**
     * 서비스 레이어에서 이미지 파싱 완료 후 호출.
     * 파싱된 imageUrls를 직접 전달받아 DTO 내 ObjectMapper 의존성 제거.
     */
    public static UserReviewResponse from(PlaceReview review, List<String> parsedImageUrls) {
        Place place = Objects.requireNonNull(review.getPlace(), "PlaceReview.place must not be null");
        return UserReviewResponse.builder()
                .reviewId(review.getId())
                .placeId(place.getId())
                .placeName(place.getName())
                .category(place.getCategory())
                .thumbnailUrl(place.getThumbnailUrl())
                .rating(review.getRating())
                .content(review.getContent())
                .imageUrls(parsedImageUrls)
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }
}
