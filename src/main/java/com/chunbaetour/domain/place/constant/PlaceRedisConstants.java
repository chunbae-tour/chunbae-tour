package com.chunbaetour.domain.place.constant;

public final class PlaceRedisConstants {

    /** 관광지 상세 정보 캐시 키 접두사 */
    public static final String PLACE_DETAIL_CACHE_PREFIX = "place:";

    /** 관광지 찜 수 카운터 키 접두사 */
    public static final String PLACE_LIKE_COUNT_PREFIX = "place:like:";

    /** 관광지 조회수 카운터 키 접두사 */
    public static final String PLACE_VIEW_COUNT_PREFIX = "place:view:";

    /** 조회수/좋아요 수 변경이 발생한 관광지 ID를 담는 Set 키 (배치 동기화용) */
    public static final String PLACE_DIRTY_STATS_KEY = "place:dirty_stats";

    /** 더티 통계 Set 장애 잔존 방지를 위한 안전망 TTL (초) */
    public static final long PLACE_DIRTY_STATS_TTL_SECONDS = 86_400L;

    /** 인기 관광지 추천 리스트 키 (ZSet) */
    public static final String RECOMMEND_POPULAR_KEY = "recommend:popular";

    /** 위치 기반 추천 관광지 데이터 키 (Geo) */
    public static final String RECOMMEND_GEO_KEY = "recommend:geo";

    /** 추천 데이터 캐시 TTL (기본 1시간) */
    public static final long RECOMMEND_CACHE_TTL_MINUTES = 60;

    /** 관광지 기반 추천 캐시 키 접두사 */
    public static final String RECOMMEND_PLACE_BASED_PREFIX = "recommend:place:";

    /** 관광지 기반 추천 캐시 TTL (30분) */
    public static final long RECOMMEND_PLACE_BASED_TTL_MINUTES = 30;

    /** 인기 점수 가중치: 찜(좋아요) */
    public static final double POPULAR_LIKE_WEIGHT = 0.7;

    /** 인기 점수 가중치: 조회수 */
    public static final double POPULAR_VIEW_WEIGHT = 0.3;

    /** 주소 지오코딩 캐시 키 접두사 — 키: geocoding::{SHA-256(query)} */
    public static final String GEOCODING_CACHE_PREFIX = "geocoding::";

    private PlaceRedisConstants() {
        // 인스턴스화 방지
    }
}
