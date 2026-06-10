package com.chunbaetour.domain.festival.service;

import com.chunbaetour.domain.festival.dto.response.CalendarEventItem;
import com.chunbaetour.domain.festival.dto.response.CalendarResponse;
import com.chunbaetour.domain.festival.dto.response.DailyCalendarResponse;
import com.chunbaetour.domain.festival.dto.response.DailyEventItem;
import com.chunbaetour.domain.festival.dto.response.FestivalCacheData;
import com.chunbaetour.domain.festival.dto.response.FestivalCacheList;
import com.chunbaetour.domain.festival.entity.Festival;
import com.chunbaetour.domain.festival.repository.FestivalQueryRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarService {

    private final FestivalQueryRepository festivalQueryRepository;

    @Lazy
    @Autowired
    private CalendarService self;

    // ── 캐시 레이어 (progressStatus 없는 entity 캐시) ───────────────────────

    @Cacheable(value = "calendar:daily", key = "#date")
    public FestivalCacheList findCachedDailyFestivals(LocalDate date) {
        // 캐시 루트를 객체로 만들기 위해 래퍼로 감싼다 — 루트 배열 타입정보 부재로 인한 ClassCastException 방지 (KAN-264).
        return FestivalCacheList.of(festivalQueryRepository.findActiveOnDate(date)
                .stream().map(FestivalCacheData::from).toList());
    }

    // ── KAN-96: 월별 캘린더 조회 ───────────────────────────────────────────

    @Cacheable(value = "calendar:monthly", key = "#year + ':' + #month")
    public CalendarResponse getMonthlyCalendar(int year, int month) {
        List<Festival> festivals = festivalQueryRepository.findActiveInMonth(year, month);

        YearMonth ym = YearMonth.of(year, month);
        LocalDate firstDay = ym.atDay(1);
        LocalDate lastDay = ym.atEndOfMonth();

        Map<LocalDate, List<CalendarEventItem>> events = new TreeMap<>();
        for (Festival f : festivals) {
            LocalDate rangeStart = f.getStartDate().isBefore(firstDay) ? firstDay : f.getStartDate();
            LocalDate rangeEnd = f.getEndDate().isAfter(lastDay) ? lastDay : f.getEndDate();
            LocalDate day = rangeStart;
            while (!day.isAfter(rangeEnd)) {
                events.computeIfAbsent(day, d -> new ArrayList<>()).add(CalendarEventItem.of(f));
                day = day.plusDays(1);
            }
        }

        return new CalendarResponse(year, month, new ArrayList<>(events.keySet()), events);
    }

    // ── KAN-97: 일별 캘린더 조회 ───────────────────────────────────────────

    public DailyCalendarResponse getDailyCalendar(LocalDate date) {
        LocalDate today = LocalDate.now();
        List<DailyEventItem> events = self.findCachedDailyFestivals(date).festivals().stream()
                .map(d -> DailyEventItem.fromCache(d, today))
                .toList();
        return new DailyCalendarResponse(date, events);
    }
}
