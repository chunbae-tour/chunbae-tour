package com.chunbaetour.domain.place.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.place.client.TourApiPlaceClient;
import com.chunbaetour.domain.place.client.TourApiPlaceItem;
import com.chunbaetour.domain.place.dto.response.PlaceSyncResult;
import com.chunbaetour.domain.place.entity.PlaceSyncState;
import com.chunbaetour.domain.place.repository.PlaceSyncStateRepository;
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
 * 관광지 KorService2 증분 동기화 오케스트레이션 (KAN-221).
 *
 * <p>스케줄러 자동 수집 + 관리자 수동 트리거 공통 진입점. ShedLock {@link LockProvider}로 다중 인스턴스
 * 중복 실행을 차단한다. HTTP fetch는 트랜잭션 밖에서 수행하고, 영속화는 {@link PlaceSyncBatchService}의
 * REQUIRES_NEW 아이템 단위 트랜잭션에 위임한다.
 *
 * <p>증분: {@link PlaceSyncState}의 lastModifiedTime 이후 변경분만 수집한다(최초엔 전수). 수집 후 가장 최신
 * modifiedtime을 상태에 저장한다. {@code showflag != "1"} 항목은 삭제(soft delete)로 반영한다.
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
    private final PlaceSyncStateRepository syncStateRepository;
    private final LockProvider lockProvider;

    /**
     * 스케줄 자동 동기화. 증분이라 변경분만 처리 → 매일 돌려도 부하가 작다.
     * 락 획득 실패(다른 인스턴스 수집 중)는 조용히 건너뛴다. 프로퍼티 부재 시 "-"(비활성) 폴백.
     */
    @Scheduled(cron = "${tour-api.kor-service.place-sync-cron:-}", zone = "Asia/Seoul")
    public void scheduledSync() {
        try {
            PlaceSyncResult result = syncAllPlaces();
            log.info("관광지 스케줄 동기화 완료: fetched={}, created={}, updated={}, deleted={}, skipped={}",
                    result.fetched(), result.created(), result.updated(), result.deleted(), result.skipped());
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.PLACE_SYNC_IN_PROGRESS) {
                log.warn("관광지 스케줄 동기화 건너뜀 — 다른 인스턴스 수집 중");
                return;
            }
            throw e;
        }
    }

    /**
     * 관광지 증분 동기화 공통 진입점. ShedLock으로 다중 인스턴스 중복 실행 차단.
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
        // 1) 마지막 동기화 경계(lastModifiedTime) 로드 — 없으면 null(최초 전수 수집)
        String since = syncStateRepository.findById(PlaceSyncState.SINGLETON_ID)
                .map(PlaceSyncState::getLastModifiedTime)
                .orElse(null);

        // 2) HTTP 수집은 트랜잭션 밖 — 경계 이후 변경분만
        List<TourApiPlaceItem> items = placeClient.fetchModifiedSince(since);

        // 3) 아이템 단위 REQUIRES_NEW 처리 — 개별 실패가 전체를 중단시키지 않음
        int created = 0, updated = 0, deleted = 0, skipped = 0;
        String maxModified = since;
        // 처리 실패(예외)한 item 중 가장 과거 modifiedtime. 경계를 이 값보다 더 전진시키지 않아
        // 다음 run에서 실패 item이 반드시 재수집되게 한다. (경계를 초과하면 조기 종료(< since)로 영구 누락)
        String minFailedModified = null;
        for (TourApiPlaceItem item : items) {
            try {
                if (item.isDeleted()) {
                    if (batchService.markDeleted(item.contentId()) == UpsertResult.DELETED) {
                        deleted++;
                    } else {
                        skipped++;
                    }
                } else {
                    switch (batchService.upsertItem(item)) {
                        case CREATED -> created++;
                        case UPDATED -> updated++;
                        default -> skipped++;
                    }
                }
                // 성공한 item만 경계 전진에 반영 — 실패 item의 modifiedtime은 경계를 끌어올리지 않는다.
                maxModified = laterOf(maxModified, item.modifiedTime());
            } catch (Exception e) {
                log.warn("관광지 item 건너뜀 — 처리 예외: contentId={}, error={}",
                        item.contentId(), e.getMessage());
                // 실패 item의 modifiedtime을 경계 캡 후보로 누적 (다음 run 재시도 보장)
                minFailedModified = earlierOf(minFailedModified, item.modifiedTime());
                skipped++;
            }
        }

        // 4) 다음 증분의 경계 갱신 (이번에 처리한 가장 최신 modifiedtime).
        //    maxModified가 since와 동일하면 경계가 전진하지 않은 것 — 수집 항목 전부 modifiedtime이 비었거나
        //    경계와 같은 초란 뜻이다. 옛 경계를 무의미하게 재저장(silent no-op)하지 않고 데이터 품질 경고만 남긴다.
        // 경계 = 성공分 최신 modifiedtime. 단 실패 item이 있으면 그 최소 modifiedtime을 넘지 않도록 캡한다
        // → 경계 이상인 실패 item이 다음 run에 재수집되어 재시도된다(멱등 upsert라 안전).
        String boundary = (minFailedModified != null)
                ? earlierOf(maxModified, minFailedModified)
                : maxModified;
        if (!items.isEmpty() && boundary != null && !boundary.equals(since)) {
            batchService.saveLastModifiedTime(boundary);
        } else if (!items.isEmpty()) {
            log.warn("관광지 동기화 경계 전진 없음 — modifiedtime이 비었거나 경계와 동일/실패 item으로 캡됨: items={}, since={}",
                    items.size(), since);
        }

        return new PlaceSyncResult(items.size(), created, updated, deleted, skipped);
    }

    /** 두 modifiedtime(yyyyMMddHHmmss 문자열) 중 더 최신(사전순 큰) 값을 반환. null 안전. */
    private String laterOf(String a, String b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.compareTo(b) >= 0 ? a : b;
    }

    /** 두 modifiedtime 중 더 과거(사전순 작은) 값을 반환. null은 "제약 없음"으로 보고 다른 값을 반환. */
    private String earlierOf(String a, String b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.compareTo(b) <= 0 ? a : b;
    }
}
