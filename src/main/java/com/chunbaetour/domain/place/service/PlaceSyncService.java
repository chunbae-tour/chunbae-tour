package com.chunbaetour.domain.place.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.place.client.TourApiPlaceClient;
import com.chunbaetour.domain.place.client.TourApiPlaceItem;
import com.chunbaetour.domain.place.dto.response.PlaceSyncResult;
import com.chunbaetour.domain.place.entity.PlaceSyncState;
import com.chunbaetour.domain.place.repository.PlaceSyncStateRepository;
import com.chunbaetour.domain.place.service.PlaceSyncBatchService.ChunkResult;
import com.chunbaetour.domain.place.service.PlaceSyncBatchService.UpsertResult;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
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
    /** 관광지 동기화 백그라운드 실행기 (AsyncConfig의 placeSyncExecutor 빈 — 필드명으로 주입 매칭). */
    private final Executor placeSyncExecutor;

    private LockConfiguration syncLockConfiguration() {
        return new LockConfiguration(Instant.now(), LOCK_NAME, LOCK_AT_MOST_FOR, LOCK_AT_LEAST_FOR);
    }

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
        Optional<SimpleLock> lock = lockProvider.lock(syncLockConfiguration());
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

    /**
     * 관리자 수동 동기화 시작 (KAN-262). 중복 정책을 API 레벨에서 일관되게 맞추기 위해 <b>요청 스레드에서
     * ShedLock을 먼저 획득</b>한다. 이미 수집 중이면 {@link ErrorCode#PLACE_SYNC_IN_PROGRESS}(409)를 호출자
     * (컨트롤러)로 전파한다.
     *
     * <p>락을 얻은 경우에만 백그라운드 실행기로 {@link #doSync()}를 위임하고, 완료/실패 시 finally에서 unlock 한다.
     * {@code JdbcTemplateLockProvider}는 thread-bound가 아니라(DB 행 기반) 요청 스레드 획득 → 워커 unlock이 안전하다.
     *
     * <p>기존 {@code @Async} 워커에서 락을 잡던 방식의 문제를 해결한다:
     * (1) 409가 워커에서 catch/log로 끝나 클라이언트에 전달되지 않던 것, (2) executor 큐(capacity=10)에 대기한
     * 요청들이 첫 동기화 종료 후 락을 재획득해 전체 동기화를 연속 실행하던 것. 이제 중복 요청은 제출 전에 409로 거절돼
     * 큐에 쌓이지 않는다.
     */
    public void startAsyncSync() {
        // 요청 스레드에서 락 판정 — 미획득(이미 수집 중)이면 409를 컨트롤러로 전파(백그라운드 큐에 적재하지 않음).
        Optional<SimpleLock> lock = lockProvider.lock(syncLockConfiguration());
        if (lock.isEmpty()) {
            log.warn("관광지 수동 동기화 거절 — 이미 수집 중 (PLACE_SYNC_IN_PROGRESS)");
            throw new BusinessException(ErrorCode.PLACE_SYNC_IN_PROGRESS);
        }
        // 락 보유 상태로 백그라운드 위임 — 완료 시 unlock.
        try {
            placeSyncExecutor.execute(() -> {
                try {
                    PlaceSyncResult result = doSync();
                    log.info("관광지 수동 동기화 완료: fetched={}, created={}, updated={}, deleted={}, skipped={}",
                            result.fetched(), result.created(), result.updated(), result.deleted(), result.skipped());
                } catch (Exception e) {
                    log.error("관광지 수동 동기화 실패", e);
                } finally {
                    lock.get().unlock();
                }
            });
        } catch (RejectedExecutionException e) {
            // 제출 거절(셧다운/큐 포화 등 극단 케이스) — 워커 finally가 실행되지 않으므로 요청 스레드에서
            // 획득한 락을 즉시 해제한다(LOCK_AT_MOST_FOR 만료를 기다리지 않고 self-heal). 거절은 호출자로 전파.
            lock.get().unlock();
            log.error("관광지 수동 동기화 제출 거절 — 락 해제 후 전파", e);
            throw e;
        }
    }

    private PlaceSyncResult doSync() {
        log.info("관광지 동기화 시작 — 페이지 단위 즉시 저장");
        // 1) 마지막 동기화 경계(lastModifiedTime) 로드 — 없으면 null(최초 전수 수집)
        String since = syncStateRepository.findById(PlaceSyncState.SINGLETON_ID)
                .map(PlaceSyncState::getLastModifiedTime)
                .orElse(null);

        // 2) HTTP 수집(트랜잭션 밖)을 페이지 콜백으로 받아 한 페이지씩 즉시 저장한다 — 전량 수집 대기 없음.
        SyncProgress p = new SyncProgress(since);
        int[] pageNo = {0};
        placeClient.fetchModifiedSince(since, page -> {
            pageNo[0]++;
            processPage(page, p);
            log.info("관광지 페이지 저장 완료: page={}, 누적 fetched={}, created={}, updated={}, deleted={}, skipped={}",
                    pageNo[0], p.fetched, p.created, p.updated, p.deleted, p.skipped);
        });

        // 3) 다음 증분의 경계 갱신. 경계 = 성공分 최신 modifiedtime. 실패 item이 있으면 그 최소 modifiedtime을
        //    넘지 않도록 캡해 다음 run에 재수집되게 한다(멱등 upsert라 안전).
        String boundary = (p.minFailedModified != null)
                ? earlierOf(p.maxModified, p.minFailedModified)
                : p.maxModified;
        if (p.fetched > 0 && boundary != null && !boundary.equals(since)) {
            batchService.saveLastModifiedTime(boundary);
        } else if (p.fetched > 0) {
            log.warn("관광지 동기화 경계 전진 없음 — modifiedtime이 비었거나 경계와 동일/실패 item으로 캡됨: fetched={}, since={}",
                    p.fetched, since);
        }

        log.info("관광지 동기화 완료: fetched={}, created={}, updated={}, deleted={}, skipped={}",
                p.fetched, p.created, p.updated, p.deleted, p.skipped);
        return new PlaceSyncResult(p.fetched, p.created, p.updated, p.deleted, p.skipped);
    }

    /**
     * 한 페이지(청크)를 처리한다. 삭제(showflag) 항목은 드물어 단건 처리하고, upsert 항목은 청크 batch로 저장한다.
     * 청크 batch가 제약/불변식 위반으로 실패하면 단건({@link #processSingleItem})으로 fallback해 실패 item만 격리한다.
     * 인프라 장애({@link DataAccessException})는 잡지 않아 전파된다(fail-fast, KAN-221 계약).
     */
    private void processPage(List<TourApiPlaceItem> items, SyncProgress p) {
        p.fetched += items.size();
        List<TourApiPlaceItem> upserts = new ArrayList<>();
        for (TourApiPlaceItem item : items) {
            if (item.isDeleted()) {
                processSingleItem(item, p);
            } else {
                upserts.add(item);
            }
        }
        if (upserts.isEmpty()) {
            return;
        }
        try {
            // happy path — 청크 batch upsert (단건 saveAndFlush 반복 제거)
            ChunkResult r = batchService.upsertChunk(upserts);
            p.created += r.created();
            p.updated += r.updated();
            p.skipped += r.skipped();
            // 청크 전체 성공 → 청크 내 모든 item의 modifiedtime으로 경계 전진
            for (TourApiPlaceItem item : upserts) {
                p.maxModified = laterOf(p.maxModified, normalizeModifiedTime(item.modifiedTime()));
            }
        } catch (DataIntegrityViolationException | IllegalArgumentException e) {
            // 청크 내 1건의 제약/불변식 위반이 청크 전체를 롤백 → 단건으로 재처리해 실패 item만 격리.
            log.warn("관광지 청크 batch 실패 — 단건 fallback 격리: size={}, error={}", upserts.size(), e.getMessage());
            for (TourApiPlaceItem item : upserts) {
                processSingleItem(item, p);
            }
        }
        // 인프라 DataAccessException은 잡지 않음 → 전파(fail-fast)
    }

    /**
     * 단건 처리(삭제/upsert) + 경계 반영. 청크 fallback과 삭제 항목에 공통으로 쓰인다.
     * 경계 캡 규칙은 KAN-221과 동일: 성공/DIV(영구)는 경계 전진, retryable 실패는 minFailedModified로 캡, 인프라는 전파.
     */
    private void processSingleItem(TourApiPlaceItem item, SyncProgress p) {
        // 공백/빈 modifiedtime은 null로 정규화 — bound=""이 경계로 저장돼 커서가 깨지는 것을 막는다.
        String itemModified = normalizeModifiedTime(item.modifiedTime());
        try {
            if (item.isDeleted()) {
                if (batchService.markDeleted(item.contentId()) == UpsertResult.DELETED) {
                    p.deleted++;
                } else {
                    p.skipped++;
                }
            } else {
                switch (batchService.upsertItem(item)) {
                    case CREATED -> p.created++;
                    case UPDATED -> p.updated++;
                    default -> p.skipped++;
                }
            }
            p.maxModified = laterOf(p.maxModified, itemModified);
        } catch (DataIntegrityViolationException e) {
            // 제약 위반 등 영구 실패 — 경계를 이 item까지 전진시켜 다음 run에 재수집(매 run 재수집 방지). minFailedModified 캡 안 함.
            p.maxModified = laterOf(p.maxModified, itemModified);
            log.warn("관광지 item 건너뜀 — 제약 위반(영구 실패 추정, 경계 전진): contentId={}, error={}",
                    item.contentId(), e.getMessage());
            p.skipped++;
        } catch (DataAccessException e) {
            // 인프라 예외 — per-item skip으로 흡수하지 않고 전파해 배치 전체 중단(fail-fast).
            log.error("관광지 동기화 중단 — 인프라 예외 전파(fail-fast): contentId={}", item.contentId(), e);
            throw e;
        } catch (Exception e) {
            // 재시도 가치 있는 실패 — 경계를 이 item 이전으로 캡해 다음 run 재수집 보장.
            log.warn("관광지 item 건너뜀 — 처리 예외(재시도 대상): contentId={}, error={}",
                    item.contentId(), e.getMessage());
            p.minFailedModified = earlierOf(p.minFailedModified, itemModified);
            p.skipped++;
        }
    }

    /** 페이지 콜백 간 누적되는 동기화 진행 상태(집계 + 경계 계산용 modifiedtime). */
    private static final class SyncProgress {
        private int fetched, created, updated, deleted, skipped;
        private String maxModified;
        private String minFailedModified;

        private SyncProgress(String since) {
            this.maxModified = since;
        }
    }

    /**
     * modifiedtime을 경계 계산용으로 정규화. yyyyMMddHHmmss(14자리 숫자)가 아니면 null로 본다.
     * 공백/빈 문자열은 물론, 이상 포맷이 경계로 저장돼 saveLastModifiedTime 도메인 검증에서 예외가 터지는 것을
     * 사전 차단한다(아이템 upsert는 REQUIRES_NEW로 이미 커밋됐는데 경계만 저장 실패하는 애매한 상태 방지).
     */
    private String normalizeModifiedTime(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.matches("\\d{14}") ? trimmed : null;
    }

    /** 두 modifiedtime(yyyyMMddHHmmss 문자열) 중 더 최신(사전순 큰) 값을 반환. 공백/null 안전. */
    private String laterOf(String a, String b) {
        a = normalizeModifiedTime(a);
        b = normalizeModifiedTime(b);
        if (a == null) return b;
        if (b == null) return a;
        return a.compareTo(b) >= 0 ? a : b;
    }

    /** 두 modifiedtime 중 더 과거(사전순 작은) 값을 반환. 공백/null은 "제약 없음"으로 보고 다른 값을 반환. */
    private String earlierOf(String a, String b) {
        a = normalizeModifiedTime(a);
        b = normalizeModifiedTime(b);
        if (a == null) return b;
        if (b == null) return a;
        return a.compareTo(b) <= 0 ? a : b;
    }
}
