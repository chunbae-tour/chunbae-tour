package com.chunbaetour.domain.festival.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 월별 캘린더 응답 (KAN-96).
 * markedDates: 축제 있는 날짜 목록.
 * events: 날짜 → 해당 날짜를 포함하는 축제 목록 Map.
 */
public record CalendarResponse(
        int year,
        int month,
        List<LocalDate> markedDates,
        Map<LocalDate, List<CalendarEventItem>> events
) {}
