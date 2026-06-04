package com.chunbaetour.domain.market.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 전통시장 데이터 동기화 스케줄러.
 * 공공데이터포털 API를 정기적으로 호출. @SchedulerLock으로 다중 인스턴스 중복 실행 방지.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MarketSyncScheduler {

    private final MarketSyncService marketSyncService;

    /**
     * 월 1회 자동 동기화 (매월 1일 밤 2시).
     * @SchedulerLock: 분산 락으로 한 인스턴스만 실행. lockAtMostFor는 동기화 소요 시간(~30초) + 여유.
     */
    @Scheduled(cron = "0 0 2 1 * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "market_sync", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void scheduledSync() {
        try {
            int synced = marketSyncService.syncAllMarkets();
            log.info("[MarketSync] 정기 동기화 완료: {} 건", synced);
        } catch (Exception e) {
            log.error("[MarketSync] 정기 동기화 실패", e);
        }
    }
}
