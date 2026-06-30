package com.chunbaetour.domain.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

@Slf4j
public class LoggingCacheErrorHandler implements CacheErrorHandler {

    // GET 실패 → 예외 삼킴(캐시미스로 처리) — 패키지 이동/리네임 후 역직렬화 충돌 시 500 방지
    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        log.warn("캐시 GET 실패. cache={}, key={}: {}", cache.getName(), key, exception.getMessage());
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        log.warn("캐시 PUT 실패. cache={}, key={}: {}", cache.getName(), key, exception.getMessage());
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        log.warn("캐시 EVICT 실패. cache={}, key={}: {}", cache.getName(), key, exception.getMessage());
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        log.warn("캐시 CLEAR 실패. cache={}: {}", cache.getName(), exception.getMessage());
    }
}
