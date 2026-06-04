package com.chunbaetour.domain.companionreview.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class CompanionReviewEventHandler {

    // commit 이후 evict — commit 전 동시 조회의 stale 재캐싱 방지
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @CacheEvict(value = "companionScore", key = "#event.targetUserId()")
    public void handleScoreCacheEvict(CompanionScoreCacheEvictEvent event) {
        log.debug("companionScore 캐시 evict. targetUserId={}", event.targetUserId());
    }
}
