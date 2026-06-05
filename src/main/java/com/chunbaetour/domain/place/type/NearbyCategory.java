package com.chunbaetour.domain.place.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 카카오 카테고리 검색 그룹 코드
 */
@Getter
@RequiredArgsConstructor
public enum NearbyCategory {
    RESTAURANT("FD6"), // 음식점
    CAFE("CE7"),       // 카페
    ACCOMMODATION("AD5"); // 숙박

    private final String code;
}
