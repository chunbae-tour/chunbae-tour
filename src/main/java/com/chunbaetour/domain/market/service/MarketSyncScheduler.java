package com.chunbaetour.domain.market.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 전통시장 데이터 동기화 스케줄러.
 * 공공데이터포털 API 호출을 정기적으로 실행.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MarketSyncScheduler {

    private final MarketSyncService marketSyncService;

    /**
     * 월 1회 자동 동기화 (매월 1일 밤 2시).
     */
    @Scheduled(cron = "0 0 2 1 * *", zone = "Asia/Seoul")
    public void scheduledSync() {
        try {
            int synced = marketSyncService.syncAllMarkets();
            log.info("[MarketSync] 정기 동기화 완료: {} 건", synced);
        } catch (Exception e) {
            log.error("[MarketSync] 정기 동기화 실패", e);
        }
    }
}
