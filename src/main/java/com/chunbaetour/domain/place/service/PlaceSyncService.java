package com.chunbaetour.domain.place.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.place.client.TourApiPlaceClient;
import com.chunbaetour.domain.place.client.TourApiPlaceItem;
import com.chunbaetour.domain.place.dto.response.PlaceSyncResult;
import com.chunbaetour.domain.place.service.PlaceSyncBatchService.UpsertResult;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 관광지 KorService2 수집 오케스트레이션 (KAN-221 Tier-1).
 *
 * <p>스케줄러 자동 수집 + 관리자 수동 트리거 공통 진입점. ShedLock {@link LockProvider}로 다중 인스턴스
 * 중복 실행을 차단한다(축제·전통시장과 동일 패턴). HTTP fetch는 트랜잭션 밖에서 수행하고, 영속화는
 * {@link PlaceSyncBatchService}의 REQUIRES_NEW 아이템 단위 트랜잭션에 위임한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceSyncService {

    private static final String   LOCK_NAME         = "place_sync_all";
    private static final Duration LOCK_AT_MOST_FOR  = Duration.ofMinutes(30);
    private static final Duration LOCK_AT_LEAST_FOR = Duration.ofMinutes(1);

    private final TourApiPlaceClient placeClient;
    private final PlaceSyncBatchService batchService;
    private final LockProvider lockProvider;

    /** 스케줄 자동 수집. 락 획득 실패(다른 인스턴스 수집 중)는 조용히 건너뛴다. */
    // 프로퍼티 부재 시 "-"(Scheduled.CRON_DISABLED)로 폴백 → 스케줄 비활성, 컨텍스트 기동 실패 방지.
    @Scheduled(cron = "${tour-api.kor-service.place-sync-cron:-}", zone = "Asia/Seoul")
    public void scheduledSync() {
        try {
            PlaceSyncResult result = syncAllPlaces();
            log.info("관광지 스케줄 수집 완료: fetched={}, created={}, updated={}, skipped={}",
                    result.fetched(), result.created(), result.updated(), result.skipped());
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.PLACE_SYNC_IN_PROGRESS) {
                log.warn("관광지 스케줄 수집 건너뜀 — 다른 인스턴스 수집 중");
                return;
            }
            throw e;
        }
    }

    /**
     * 전국 관광지 수집 공통 진입점. ShedLock으로 다중 인스턴스 중복 실행 차단.
     * 락 획득 실패 시 {@link ErrorCode#PLACE_SYNC_IN_PROGRESS} 예외.
     */
    public PlaceSyncResult syncAllPlaces() {
        LockConfiguration lockConfig = new LockConfiguration(
                Instant.now(), LOCK_NAME, LOCK_AT_MOST_FOR, LOCK_AT_LEAST_FOR);
        Optional<SimpleLock> lock = lockProvider.lock(lockConfig);
        if (lock.isEmpty()) {
            log.warn("place_sync_all 락 획득 실패 — 이미 수집 중인 인스턴스 존재");
            throw new BusinessException(ErrorCode.PLACE_SYNC_IN_PROGRESS);
        }
        try {
            return doSync();
        } finally {
            lock.get().unlock();
        }
    }

    private PlaceSyncResult doSync() {
        // 1) HTTP 수집은 트랜잭션 밖 (네트워크 지연이 DB 커넥션을 잡지 않도록)
        List<TourApiPlaceItem> items = placeClient.fetchAll();

        // 2) 아이템 단위 REQUIRES_NEW upsert — 개별 실패가 전체를 중단시키지 않음
        int created = 0, updated = 0, skipped = 0;
        for (TourApiPlaceItem item : items) {
            try {
                UpsertResult result = batchService.upsertItem(item);
                switch (result) {
                    case CREATED -> created++;
                    case UPDATED -> updated++;
                    case SKIPPED -> skipped++;
                }
            } catch (Exception e) {
                // 제약 위반(중복 등) 등 개별 아이템 예외는 skip 집계
                log.warn("관광지 item 건너뜀 — upsert 예외: contentId={}, error={}",
                        item.contentId(), e.getMessage());
                skipped++;
            }
        }

        return new PlaceSyncResult(items.size(), created, updated, skipped);
    }
}
