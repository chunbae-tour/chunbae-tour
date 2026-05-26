package com.chunbaetour.domain.place.constant;

public final class PlaceRedisConstants {

    /** 관광지 상세 정보 캐시 키 접두사 */
    public static final String PLACE_DETAIL_CACHE_PREFIX = "place:";

    /** 관광지 찜 수 카운터 키 접두사 */
    public static final String PLACE_LIKE_COUNT_PREFIX = "place:like:";

    /** 관광지 조회수 카운터 키 접두사 */
    public static final String PLACE_VIEW_COUNT_PREFIX = "place:view:";

    private PlaceRedisConstants() {
        // 인스턴스화 방지
    }
}
