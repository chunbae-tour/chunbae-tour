package com.chunbaetour.domain.companion.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 동행 생애주기 자동화 배치 스케줄러 (고도화 #2, KAN-299).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanionStatusScheduler {

    private final CompanionService companionService;

    // 매일 자정(Asia/Seoul) — tripEndDate < today인 ONGOING 동행을 일괄 ENDED 전환
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "companion_status_end_expired", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void endExpiredCompanions() {
        try {
            int updated = companionService.endExpiredCompanions();
            log.info("Companion status batch: {} companion(s) transitioned to ENDED", updated);
        } catch (Exception e) {
            log.error("Companion status batch failed", e);
        }
    }
}
