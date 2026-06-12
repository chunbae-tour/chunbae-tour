package com.chunbaetour.domain.place.constant;

public final class PlaceRedisConstants {

    /** 관광지 상세 정보 캐시 키 접두사 */
    public static final String PLACE_DETAIL_CACHE_PREFIX = "place:";

    /** 관광지 상세 정보 캐시 TTL (기본 10분) */
    public static final long PLACE_DETAIL_CACHE_TTL_MINUTES = 10;

    /** 관광지 찜 수 카운터 키 접두사 */
    public static final String PLACE_LIKE_COUNT_PREFIX = "place:like:";

    /** 관광지 조회수 카운터 키 접두사 */
    public static final String PLACE_VIEW_COUNT_PREFIX = "place:view:";

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

    /** 서버 시작 시 캐시 웜업 대상 관광지 상세 수 — 인기 Top N개를 미리 적재 */
    public static final int CACHE_WARMUP_DETAIL_TOP_N = 20;

    /** 캐시 웜업 시 Redis 재계산을 위해 DB에서 조회할 후보군 수 */
    public static final int CACHE_WARMUP_DB_CANDIDATE_TOP_N = 100;

    /** 다중 인스턴스 환경에서 캐시 웜업 동시 실행 방지를 위한 분산 락 키 */
    public static final String CACHE_WARMUP_LOCK_KEY = "lock:cache-warmup";

    /** ZSet 인기 추천 웜업 대상 수 */
    public static final int CACHE_WARMUP_ZSET_TOP_N = 10;

    /** 캐시 웜업 각 요청 간 간격(ms) — DB/Redis 부하 분산 목적 */
    public static final long CACHE_WARMUP_INTERVAL_MS = 50;

    private PlaceRedisConstants() {
        // 인스턴스화 방지
    }
}
