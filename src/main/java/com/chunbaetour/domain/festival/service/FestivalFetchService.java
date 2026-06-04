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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FestivalFetchService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final Map<String, String> REGION_MAP = Map.ofEntries(
            Map.entry("11", "서울특별시"),
            Map.entry("26", "부산광역시"),
            Map.entry("27", "대구광역시"),
            Map.entry("28", "인천광역시"),
            Map.entry("29", "광주광역시"),
            Map.entry("30", "대전광역시"),
            Map.entry("31", "울산광역시"),
            Map.entry("36", "세종특별자치시"),
            Map.entry("41", "경기도"),
            Map.entry("42", "강원특별자치도"),
            Map.entry("43", "충청북도"),
            Map.entry("44", "충청남도"),
            Map.entry("45", "전북특별자치도"),
            Map.entry("46", "전라남도"),
            Map.entry("47", "경상북도"),
            Map.entry("48", "경상남도"),
            Map.entry("50", "제주특별자치도")
    );

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
            Optional<Festival> existing = festivalRepository.findByExternalId(item.contentid());
            if (existing.isEmpty()) {
                festivalRepository.save(toNewFestival(item));
                return true;
            }
            Festival festival = existing.get();
            if (festival.getStatus() == FestivalStatus.DELETED) {
                log.warn("Festival item skipped — DELETED status preserved: contentid={}", item.contentid());
                return false;
            }
            festival.updateFromApi(
                    item.title(),
                    resolveRegion(item.lDongRegnCd(), item.addr1()),
                    item.addr1(),
                    parseDate(item.eventstartdate()),
                    parseDate(item.eventenddate()),
                    item.firstimage()
            );
            return false;
        } catch (Exception e) {
            log.warn("Festival item skipped: contentid={}, reason={}", item.contentid(), e.getMessage());
            return false;
        }
    }

    private Festival toNewFestival(TourApiFestivalItem item) {
        return Festival.createFromApi(
                item.contentid(),
                item.title(),
                resolveRegion(item.lDongRegnCd(), item.addr1()),
                item.addr1(),
                parseDate(item.eventstartdate()),
                parseDate(item.eventenddate()),
                item.firstimage()
        );
    }

    private boolean isValid(TourApiFestivalItem item) {
        if (item.contentid() == null || item.contentid().isBlank()) return false;
        if (item.title() == null || item.title().isBlank()) return false;
        if (item.addr1() == null || item.addr1().isBlank()) return false;
        if (item.eventstartdate() == null || item.eventstartdate().isBlank()) return false;
        if (item.eventenddate() == null || item.eventenddate().isBlank()) return false;
        try {
            LocalDate start = parseDate(item.eventstartdate());
            LocalDate end   = parseDate(item.eventenddate());
            if (start.isAfter(end)) return false;
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    private LocalDate parseDate(String yyyymmdd) {
        return LocalDate.parse(yyyymmdd, DATE_FMT);
    }

    private String resolveRegion(String lDongRegnCd, String addr1) {
        if (lDongRegnCd != null && REGION_MAP.containsKey(lDongRegnCd)) {
            return REGION_MAP.get(lDongRegnCd);
        }
        if (addr1 != null && !addr1.isBlank()) {
            String[] parts = addr1.split(" ");
            return parts[0];
        }
        return "기타";
    }
}
