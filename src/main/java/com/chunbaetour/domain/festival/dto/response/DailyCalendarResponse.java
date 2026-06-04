package com.chunbaetour.domain.festival.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * 일별 캘린더 응답 (KAN-97).
 */
public record DailyCalendarResponse(
        LocalDate date,
        List<DailyEventItem> events
) {}
