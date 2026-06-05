package com.chunbaetour.domain.festival.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.festival.client.TourApiClient;
import com.chunbaetour.domain.festival.client.TourApiFestivalItem;
import com.chunbaetour.domain.festival.dto.response.FestivalFetchResult;
import com.chunbaetour.domain.festival.entity.Festival;
import com.chunbaetour.domain.festival.repository.FestivalRepository;
import com.chunbaetour.domain.festival.type.FestivalStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FestivalFetchService {

    private static final String  LOCK_NAME         = "festival_fetch";
    private static final Duration LOCK_AT_MOST_FOR  = Duration.ofMinutes(30);
    private static final Duration LOCK_AT_LEAST_FOR = Duration.ofMinutes(1);

    private final TourApiClient      tourApiClient;
    private final FestivalRepository festivalRepository;
    private final FestivalCacheEvictUtil cacheEvict;
    private final LockProvider       lockProvider;

    @Autowired @Lazy private FestivalFetchService self;

    enum UpsertResult { CREATED, UPDATED, SKIPPED }

    @Scheduled(cron = "${tour-api.sync-cron}", zone = "Asia/Seoul")
    public void scheduledFetch() {
        try {
            FestivalFetchResult result = fetchNow();
            log.info("Festival scheduled fetch complete: fetched={}, created={}, updated={}, skipped={}",
                    result.fetched(), result.created(), result.updated(), result.skipped());
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.FESTIVAL_FETCH_IN_PROGRESS) {
                log.warn("Festival scheduled fetch skipped — 다른 인스턴스 수집 중");
                return;
            }
            throw e;
        }
    }

    /**
     * 스케줄러 자동 수집 + 관리자 수동 트리거 공통 진입점.
     * LockProvider로 직접 락을 획득해 스케줄러·수동 동시 실행을 방지한다.
     * 락 획득 실패(다른 인스턴스 또는 스케줄러 실행 중)이면 즉시 반환한다.
     */
    public FestivalFetchResult fetchNow() {
        LockConfiguration lockConfig = new LockConfiguration(
                Instant.now(), LOCK_NAME, LOCK_AT_MOST_FOR, LOCK_AT_LEAST_FOR);
        Optional<SimpleLock> lock = lockProvider.lock(lockConfig);
        if (lock.isEmpty()) {
            log.warn("festival_fetch 락 획득 실패 — 이미 수집 중인 인스턴스 존재");
            throw new BusinessException(ErrorCode.FESTIVAL_FETCH_IN_PROGRESS);
        }
        try {
            return doFetch();
        } finally {
            lock.get().unlock();
        }
    }

    private FestivalFetchResult doFetch() {
        if (self == null) {
            throw new IllegalStateException("FestivalFetchService self proxy not injected");
        }

        List<TourApiFestivalItem> items = tourApiClient.fetchAll();
        int created = 0, updated = 0, skipped = 0;

        try {
            for (TourApiFestivalItem item : items) {
                if (!isValid(item)) {
                    skipped++;
                    continue;
                }
                String externalId = buildExternalId(item);
                try {
                    switch (self.upsertItem(item, externalId)) {
                        case CREATED -> created++;
                        case UPDATED -> updated++;
                        case SKIPPED -> skipped++;
                    }
                } catch (Exception e) {
                    log.warn("Festival item skipped — upsert exception: externalId={}, error={}",
                            externalId, e.getMessage());
                    skipped++;
                }
            }
        } finally {
            if (created > 0 || updated > 0) {
                cacheEvict.evictAll();
            }
        }

        return new FestivalFetchResult(items.size(), created, updated, skipped);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UpsertResult upsertItem(TourApiFestivalItem item, String externalId) {
        try {
            Optional<Festival> existing = festivalRepository.findByExternalId(externalId);
            if (existing.isEmpty()) {
                festivalRepository.save(toNewFestival(item, externalId));
                return UpsertResult.CREATED;
            }
            Festival festival = existing.get();
            if (festival.getStatus() == FestivalStatus.DELETED) {
                log.warn("Festival item skipped — DELETED status preserved: externalId={}", externalId);
                return UpsertResult.SKIPPED;
            }
            festival.updateFromApi(
                    item.fstvlNm(),
                    resolveRegion(item),
                    resolveAddress(item),
                    LocalDate.parse(item.fstvlStartDate()),
                    LocalDate.parse(item.fstvlEndDate()),
                    null
            );
            return UpsertResult.UPDATED;
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateExternalId(e)) {
                log.warn("Festival insert conflict (duplicate externalId): externalId={}", externalId);
            } else {
                log.error("Festival upsert failed due to data integrity violation: externalId={}", externalId, e);
            }
            // REQUIRES_NEW 트랜잭션은 이미 rollback-only — SKIPPED 반환 불가, re-throw로 호출부 catch에 위임
            throw e;
        } catch (IllegalArgumentException e) {
            log.warn("Festival item skipped: externalId={}, reason={}", externalId, e.getMessage());
            return UpsertResult.SKIPPED;
        }
    }

    private Festival toNewFestival(TourApiFestivalItem item, String externalId) {
        return Festival.createFromApi(
                externalId,
                item.fstvlNm(),
                resolveRegion(item),
                resolveAddress(item),
                LocalDate.parse(item.fstvlStartDate()),
                LocalDate.parse(item.fstvlEndDate()),
                null
        );
    }

    private boolean isValid(TourApiFestivalItem item) {
        if (item.insttCode() == null || item.insttCode().isBlank()) return false;
        if (item.fstvlNm() == null || item.fstvlNm().isBlank()) return false;
        if (item.fstvlNm().length() > 255) return false;
        if (item.fstvlStartDate() == null || item.fstvlStartDate().isBlank()) return false;
        if (item.fstvlEndDate() == null || item.fstvlEndDate().isBlank()) return false;
        String addr = resolveAddress(item);
        if (addr.isBlank() || addr.length() > 255) return false;
        if (resolveRegion(item).length() > 100) return false;
        try {
            LocalDate start = LocalDate.parse(item.fstvlStartDate());
            LocalDate end   = LocalDate.parse(item.fstvlEndDate());
            if (start.isAfter(end)) return false;
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    private String resolveRegion(TourApiFestivalItem item) {
        if (item.rdnmadr() != null && !item.rdnmadr().isBlank()) {
            return item.rdnmadr().split(" ")[0];
        }
        if (item.insttNm() != null && !item.insttNm().isBlank()) {
            return item.insttNm().split(" ")[0];
        }
        return "기타";
    }

    private String resolveAddress(TourApiFestivalItem item) {
        if (item.rdnmadr() != null && !item.rdnmadr().isBlank()) return item.rdnmadr();
        if (item.opar() != null && !item.opar().isBlank()) return item.opar();
        return "";
    }

    private boolean isDuplicateExternalId(DataIntegrityViolationException e) {
        Throwable root = e.getRootCause();
        if (root instanceof SQLException sqlEx) {
            return sqlEx.getErrorCode() == 1062; // MySQL: Duplicate entry
        }
        String msg = e.getMessage();
        return msg != null && msg.contains("Duplicate entry");
    }

    private String normalizeKeyPart(String value) {
        return value == null ? "" : value.trim();
    }

    private String buildExternalId(TourApiFestivalItem item) {
        String raw = normalizeKeyPart(item.insttCode()) + "|"
                + normalizeKeyPart(item.fstvlNm()) + "|"
                + normalizeKeyPart(item.fstvlStartDate());
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
