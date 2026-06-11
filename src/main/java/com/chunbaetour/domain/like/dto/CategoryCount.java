package com.chunbaetour.domain.like.dto;

import com.chunbaetour.domain.place.type.PlaceCategory;

/**
 * 유저가 찜한 관광지의 카테고리별 집계 결과 Projection.
 * <p>
 * {@link com.chunbaetour.domain.like.repository.UserLikeRepository#findLikedPlaceCategoryCountsByUserId}
 * 쿼리의 반환 타입으로 사용되며, {@code Object[]} 대신 타입 안전한 접근을 제공한다.
 * </p>
 *
 * @see PlaceCategory
 */
public interface CategoryCount {

    /** 찜한 관광지의 카테고리 */
    PlaceCategory getCategory();

    /** 해당 카테고리의 찜 횟수 */
    Long getCount();
}
