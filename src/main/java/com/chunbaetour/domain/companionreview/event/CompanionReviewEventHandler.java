package com.chunbaetour.domain.companionreview.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class CompanionReviewEventHandler {

    private final CacheManager cacheManager;

    // commit 이후 evict — @CacheEvict AOP 대신 CacheManager 직접 호출
    // @Async 도입 시 @CacheEvict AOP 체인이 프록시 컨텍스트를 잃어 silently 실패하는 문제 방지
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleScoreCacheEvict(CompanionScoreCacheEvictEvent event) {
        Cache cache = cacheManager.getCache("companionScore");
        if (cache != null) {
            cache.evict(event.targetUserId());
        }
        log.debug("companionScore 캐시 evict. targetUserId={}", event.targetUserId());
    }
}
