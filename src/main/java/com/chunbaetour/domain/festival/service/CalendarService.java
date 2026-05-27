package com.chunbaetour.domain.festival.service;

import com.chunbaetour.domain.festival.dto.response.CalendarEventItem;
import com.chunbaetour.domain.festival.dto.response.CalendarResponse;
import com.chunbaetour.domain.festival.dto.response.DailyCalendarResponse;
import com.chunbaetour.domain.festival.dto.response.DailyEventItem;
import com.chunbaetour.domain.festival.entity.Festival;
import com.chunbaetour.domain.festival.repository.FestivalQueryRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 축제 캘린더 서비스 (KAN-96/97).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarService {

    private final FestivalQueryRepository festivalQueryRepository;

    // ── KAN-96: 월별 캘린더 조회 ───────────────────────────────────────────

    /**
     * 월별 캘린더 — 해당 월에 진행 중이거나 시작되는 ACTIVE 축제.
     * markedDates: 축제 있는 날짜 목록 (중복 제거, 오름차순).
     * events: 날짜 → 해당 날짜를 포함하는 축제 목록.
     *
     * @param year  연도
     * @param month 월 (1~12)
     */
    @Cacheable(value = "calendar:monthly", key = "#year + ':' + #month")
    public CalendarResponse getMonthlyCalendar(int year, int month) {
        List<Festival> festivals = festivalQueryRepository.findActiveInMonth(year, month);

        YearMonth ym = YearMonth.of(year, month);
        LocalDate firstDay = ym.atDay(1);
        LocalDate lastDay = ym.atEndOfMonth();

        // 날짜별 이벤트 Map (TreeMap — 날짜 오름차순 정렬)
        Map<LocalDate, List<CalendarEventItem>> events = new TreeMap<>();

        for (Festival f : festivals) {
            // 해당 월 내 겹치는 날짜 범위 계산
            LocalDate rangeStart = f.getStartDate().isBefore(firstDay) ? firstDay : f.getStartDate();
            LocalDate rangeEnd = f.getEndDate().isAfter(lastDay) ? lastDay : f.getEndDate();

            LocalDate day = rangeStart;
            while (!day.isAfter(rangeEnd)) {
                events.computeIfAbsent(day, d -> new ArrayList<>()).add(CalendarEventItem.of(f));
                day = day.plusDays(1);
            }
        }

        List<LocalDate> markedDates = new ArrayList<>(events.keySet());

        return new CalendarResponse(year, month, markedDates, events);
    }

    // ── KAN-97: 일별 캘린더 조회 ───────────────────────────────────────────

    /**
     * 일별 캘린더 — 해당 날짜를 포함하는 ACTIVE 축제 목록.
     *
     * @param date 조회 날짜
     */
    @Cacheable(value = "calendar:daily", key = "#date")
    public DailyCalendarResponse getDailyCalendar(LocalDate date) {
        List<Festival> festivals = festivalQueryRepository.findActiveOnDate(date);
        List<DailyEventItem> events = festivals.stream()
                .map(DailyEventItem::of)
                .toList();
        return new DailyCalendarResponse(date, events);
    }
}
