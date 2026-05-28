package com.chunbaetour.domain.common.redis;

/**
 * 상인 홈 Redis 캐시 키 모음.
 * 조회와 무효화 경로가 같은 prefix를 사용하도록 공통 상수로 관리한다.
 */
public final class MerchantHomeCacheKeys {

    // 캐시 키 버전 프리픽스. 스키마가 바뀌면 v2로 올려 이전 캐시와 충돌을 피한다.
    public static final String CACHE_KEY_PREFIX = "merchant:home:v1:";

    private MerchantHomeCacheKeys() {
        // 인스턴스화 방지
    }
}
