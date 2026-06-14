package com.chunbaetour.domain.festival.service;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 축제 수집(evictAll) 직후 주요 캐시를 미리 채워 Cache Stampede를 방지한다.
 * 사용자 트래픽이 집중되는 핫 경로(기본 목록·당월 캘린더·오늘 일별)만 워밍한다.
 *
 * <p>@Cacheable 메서드는 Spring 프록시를 거쳐야 캐시에 저장되므로, 같은 빈의 self-invocation이 아닌
 * 별도 빈(FestivalService·CalendarService)을 주입해 호출한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FestivalCacheWarmer {

    // 축제 목록 API 기본 페이지 크기(FestivalController.getList size 기본값)와 일치시켜
    // 워밍한 캐시 키(null:null:null:10)가 실제 기본 요청과 적중하도록 한다.
    private static final int DEFAULT_LIST_SIZE = 10;

    private final FestivalService festivalService;
    private final CalendarService calendarService;

    /** 수집 변경분이 있을 때 호출. 개별 워밍 실패가 전체를 막지 않도록 각각 격리한다. */
    public void warmUp() {
        LocalDate today = LocalDate.now();
        warm("festivals:list(default)", () ->
                festivalService.findCachedFestivalList(null, null, null, DEFAULT_LIST_SIZE));
        warm("calendar:monthly(current)", () ->
                calendarService.getMonthlyCalendar(today.getYear(), today.getMonthValue()));
        warm("calendar:daily(today)", () ->
                calendarService.findCachedDailyFestivals(today));
        log.info("Festival cache warm-up complete");
    }

    private void warm(String label, Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            log.warn("Festival cache warm-up skipped — {} 실패: {}", label, e.getMessage());
        }
    }
}
