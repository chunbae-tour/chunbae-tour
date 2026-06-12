package com.chunbaetour.domain.place.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * 조회수 및 좋아요 통계 비동기 배치 동기화 스케줄러 (KAN-279).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceStatsSyncScheduler {

    private final PlaceStatsSyncBatchService placeStatsSyncBatchService;

    /**
     * 1분마다 더티 마킹된 조회수/좋아요 수를 Redis에서 DB로 동기화.
     * fixedDelay를 사용하여 이전 배치가 완전히 끝난 뒤 1분을 대기한다.
     */
    @Scheduled(fixedDelay = 60000)
    @SchedulerLock(name = "place_stats_sync", lockAtMostFor = "PT5M", lockAtLeastFor = "PT10S")
    public void syncStats() {
        try {
            placeStatsSyncBatchService.syncDirtyStats();
        } catch (Exception e) {
            log.error("Place stats sync scheduled task failed", e);
        }
    }
}
