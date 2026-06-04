package com.chunbaetour.domain.festival.service;

import com.chunbaetour.domain.festival.client.TourApiClient;
import com.chunbaetour.domain.festival.client.TourApiFestivalItem;
import com.chunbaetour.domain.festival.dto.response.FestivalFetchResult;
import com.chunbaetour.domain.festival.entity.Festival;
import com.chunbaetour.domain.festival.repository.FestivalRepository;
import com.chunbaetour.domain.festival.type.FestivalStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FestivalFetchService {

    private final TourApiClient tourApiClient;
    private final FestivalRepository festivalRepository;
    private final FestivalCacheEvictUtil cacheEvict;

    @Autowired @Lazy private FestivalFetchService self;

    @Scheduled(cron = "${tour-api.sync-cron}")
    public void scheduledFetch() {
        FestivalFetchResult result = fetchNow();
        log.info("Festival scheduled fetch complete: fetched={}, created={}, skipped={}",
                result.fetched(), result.created(), result.skipped());
    }

    public FestivalFetchResult fetchNow() {
        List<TourApiFestivalItem> items = tourApiClient.fetchAll();
        int created = 0, skipped = 0;

        for (TourApiFestivalItem item : items) {
            if (!isValid(item)) {
                skipped++;
                continue;
            }
            if (self.upsertItem(item)) {
                created++;
            } else {
                skipped++;
            }
        }

        if (created > 0) {
            cacheEvict.evictAll();
        }

        return new FestivalFetchResult(items.size(), created, skipped);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean upsertItem(TourApiFestivalItem item) {
        try {
            String externalId = item.insttCode() + "_" + item.fstvlNm();
            Optional<Festival> existing = festivalRepository.findByExternalId(externalId);
            if (existing.isEmpty()) {
                festivalRepository.save(toNewFestival(item, externalId));
                return true;
            }
            Festival festival = existing.get();
            if (festival.getStatus() == FestivalStatus.DELETED) {
                log.warn("Festival item skipped — DELETED status preserved: externalId={}", externalId);
                return false;
            }
            festival.updateFromApi(
                    item.fstvlNm(),
                    resolveRegion(item),
                    resolveAddress(item),
                    LocalDate.parse(item.fstvlStartDate()),
                    LocalDate.parse(item.fstvlEndDate()),
                    null
            );
            return false;
        } catch (Exception e) {
            log.warn("Festival item skipped: insttCode={}, fstvlNm={}, reason={}",
                    item.insttCode(), item.fstvlNm(), e.getMessage());
            return false;
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
        if (item.fstvlStartDate() == null || item.fstvlStartDate().isBlank()) return false;
        if (item.fstvlEndDate() == null || item.fstvlEndDate().isBlank()) return false;
        if (resolveAddress(item).isBlank()) return false;
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
}
