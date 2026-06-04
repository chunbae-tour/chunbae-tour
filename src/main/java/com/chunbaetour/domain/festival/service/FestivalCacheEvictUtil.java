package com.chunbaetour.domain.festival.service;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Component;

/**
 * 축제 관련 캐시 무효화 중앙 유틸.
 * 모든 축제 변경 경로(create/update/delete/batch 등)는 이 빈을 통해 evict한다.
 */
@Component
public class FestivalCacheEvictUtil {

    /** 축제 생성 시 — 특정 ID 없이 전체 무효화. */
    @Caching(evict = {
            @CacheEvict(value = "festivals",        allEntries = true),
            @CacheEvict(value = "festivals:list",   allEntries = true),
            @CacheEvict(value = "calendar:monthly", allEntries = true),
            @CacheEvict(value = "calendar:daily",   allEntries = true)
    })
    public void evictAll() {}

    /** 축제 수정/삭제 시 — 해당 ID 캐시 + 목록/캘린더 전체 무효화. */
    @Caching(evict = {
            @CacheEvict(value = "festivals",        key = "#festivalId"),
            @CacheEvict(value = "festivals:list",   allEntries = true),
            @CacheEvict(value = "calendar:monthly", allEntries = true),
            @CacheEvict(value = "calendar:daily",   allEntries = true)
    })
    public void evictById(Long festivalId) {}
}
