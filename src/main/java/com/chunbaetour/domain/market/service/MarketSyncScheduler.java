package com.chunbaetour.domain.market.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 전통시장 데이터 동기화 스케줄러.
 * 월 1회 정기 실행. 분산 락은 MarketSyncService.syncAllMarkets()에 적용
 * (스케줄러·관리자 수동 모두 커버).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MarketSyncScheduler {

    private final MarketSyncService marketSyncService;

    /**
     * 월 1회 자동 동기화 (매월 1일 밤 2시).
     * 실제 동기화는 marketSyncService.syncAllMarkets() 호출 → LockProvider 적용.
     */
    @Scheduled(cron = "0 0 2 1 * *", zone = "Asia/Seoul")
    public void scheduledSync() {
        try {
            var result = marketSyncService.syncAllMarkets();
            log.info("[MarketSync] 정기 동기화 완료 — inserted={}, updated={}, skipped={}",
                    result.insertedCount(), result.updatedCount(), result.skippedCount());
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.MARKET_SYNC_IN_PROGRESS) {
                log.info("[MarketSync] 다른 인스턴스에서 수집 중 — 스킵");
            } else {
                log.error("[MarketSync] 정기 동기화 실패", e);
            }
        } catch (Exception e) {
            log.error("[MarketSync] 정기 동기화 중 예상치 못한 오류", e);
        }
    }
}
