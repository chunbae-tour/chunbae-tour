package com.chunbaetour.domain.festival.controller;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.festival.dto.response.CalendarResponse;
import com.chunbaetour.domain.festival.dto.response.DailyCalendarResponse;
import com.chunbaetour.domain.festival.service.CalendarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "축제 캘린더", description = "월별·일별 축제 캘린더 조회 API — 비인증 허용 (/api/v1/calendar/**)")
@Validated
@RestController
@RequestMapping("/api/v1/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarService calendarService;

    @Operation(summary = "월별 캘린더 조회", description = "해당 월 ACTIVE 축제 날짜별 그룹핑. markedDates + events 반환.")
    @GetMapping
    public ApiResponse<CalendarResponse> getMonthly(
            @Min(1) @Max(9999) @RequestParam int year,
            @Min(1) @Max(12) @RequestParam int month) {
        return ApiResponse.success(calendarService.getMonthlyCalendar(year, month));
    }

    @Operation(summary = "일별 캘린더 조회", description = "특정 날짜를 포함하는 ACTIVE 축제 목록.")
    @GetMapping("/daily")
    public ApiResponse<DailyCalendarResponse> getDaily(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(calendarService.getDailyCalendar(date));
    }
}
