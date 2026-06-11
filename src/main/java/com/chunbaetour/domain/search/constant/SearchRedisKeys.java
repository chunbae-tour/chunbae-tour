package com.chunbaetour.domain.search.constant;

/**
 * 검색 도메인에서 사용하는 Redis Key 상수 모음.
 * <p>
 * 서비스 간(SearchService, PopularSearchService) 키 중복 선언으로 인한
 * 계약 불일치 및 드리프트(Drift) 위험을 방지하기 위해 공용 상수로 단일화한다.
 * </p>
 */
public final class SearchRedisKeys {

    private SearchRedisKeys() {
        // 인스턴스화 방지
    }

    /** 
     * 오늘 누적 검색 횟수 ZSet 키 (score 높을수록 인기) 
     * <br>사용: PopularSearchService (점수 증가/조회), SearchService (자동완성 보완용 ZSet 조회)
     */
    public static final String POPULAR_RANKING_KEY = "search:ranking";

    /** 
     * 전일 인기 검색어 스냅샷 ZSet 키 (자정 초기화 직전 백업) 
     * <br>사용: PopularSearchService (전날 순위 비교용)
     */
    public static final String POPULAR_RANKING_PREV_KEY = "search:ranking:prev";
    /** 
     * 오타 교정 — 관광지명 2-gram 역인덱스 키 prefix.
     * <br>Key 형식: {@code search:typo:places:gram:{gram}} → Value: Redis Set (해당 2-gram을 포함하는 장소명 목록)
     * <br>사용: TypoCorrectionService (사전 초기화/오타 후보 조회)
     */
    public static final String TYPO_GRAM_PREFIX_PLACES = "search:typo:places:gram:";

    /**
     * 오타 교정 — 축제명 2-gram 역인덱스 키 prefix.
     * <br>Key 형식: {@code search:typo:festivals:gram:{gram}} → Value: Redis Set (해당 2-gram을 포함하는 축제명 목록)
     * <br>사용: TypoCorrectionService (사전 초기화/오타 후보 조회)
     */
    public static final String TYPO_GRAM_PREFIX_FESTIVALS = "search:typo:festivals:gram:";

}
