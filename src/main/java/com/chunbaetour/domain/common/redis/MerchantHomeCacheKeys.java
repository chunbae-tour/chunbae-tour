package com.chunbaetour.domain.common.redis;

/**
 * 상인 홈 Redis 캐시 키 모음.
 * 조회와 무효화 경로가 같은 prefix를 사용하도록 공통 상수로 관리한다.
 */
public final class MerchantHomeCacheKeys {

    // 캐시 키 버전 프리픽스. 응답 스키마가 바뀌면 버전을 올려 이전 캐시 역직렬화 실패를 피한다.
    // v2: 어제 매출·시간대별 분포·미완료 카운터 메트릭 추가 (KAN-283).
    public static final String CACHE_KEY_PREFIX = "merchant:home:v2:";

    private MerchantHomeCacheKeys() {
        // 인스턴스화 방지
    }
}
