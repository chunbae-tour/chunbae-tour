package com.chunbaetour.domain.place.dto.response;

import com.chunbaetour.domain.auth.profileimage.ProfileImageDisplaySupport;
import com.chunbaetour.domain.place.PlaceReview;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;

/**
 * 관광지 리뷰 응답 DTO.
 * 리뷰 목록 조회(GET /places/{placeId}/reviews)에서 사용.
 *
 * <p>이미지 파싱은 서비스 레이어({@link com.chunbaetour.domain.place.service.PlaceReviewService})에서 처리.
 * DTO는 순수 데이터 전달 역할만 담당.
 */
@Builder
public record PlaceReviewResponse(
    Long reviewId,
    Long authorId,
    String authorNickname,
    String authorProfileImageUrl,
    int rating,
    String content,
    List<String> imageUrls,
    LocalDateTime createdAt
) {
    /**
     * 서비스 레이어에서 이미지 파싱 완료 후 호출.
     * 파싱된 imageUrls를 직접 전달받아 DTO 내 ObjectMapper 의존성 제거.
     */
    public static PlaceReviewResponse from(PlaceReview review, List<String> parsedImageUrls) {
        return PlaceReviewResponse.builder()
                .reviewId(review.getId())
                .authorId(review.getAuthor().getId())
                .authorNickname(review.getAuthor().getNickname())
                .authorProfileImageUrl(ProfileImageDisplaySupport.toDisplayUrl(review.getAuthor().getProfileImageUrl()))
                .rating(review.getRating())
                .content(review.getContent())
                .imageUrls(parsedImageUrls)
                .createdAt(review.getCreatedAt())
                .build();
    }
}
