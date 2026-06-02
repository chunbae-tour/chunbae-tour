package com.chunbaetour.domain.festival.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.festival.dto.response.CalendarResponse;
import com.chunbaetour.domain.festival.dto.response.DailyCalendarResponse;
import com.chunbaetour.domain.festival.service.CalendarService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 축제 캘린더 API (KAN-96/97). 비인증 허용.
 */
@Validated
@RestController
@RequestMapping("/api/v1/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarService calendarService;

    /**
     * 월별 캘린더 조회 (KAN-96).
     *
     * @param year  연도 (1~9999)
     * @param month 월 (1~12)
     */
    @GetMapping
    public ApiResponse<CalendarResponse> getMonthly(
            @Min(1) @Max(9999) @RequestParam int year,
            @Min(1) @Max(12) @RequestParam int month) {
        return ApiResponse.success(calendarService.getMonthlyCalendar(year, month));
    }

    /**
     * 일별 캘린더 조회 (KAN-97).
     *
     * @param date 조회 날짜 (ISO: yyyy-MM-dd)
     */
    @GetMapping("/daily")
    public ApiResponse<DailyCalendarResponse> getDaily(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(calendarService.getDailyCalendar(date));
    }
}
