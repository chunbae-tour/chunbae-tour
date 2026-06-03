package com.chunbaetour.domain.festival.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.chunbaetour.domain.festival.dto.response.CalendarResponse;
import com.chunbaetour.domain.festival.dto.response.DailyCalendarResponse;
import com.chunbaetour.domain.festival.dto.response.FestivalCacheData;
import com.chunbaetour.domain.festival.entity.Festival;
import com.chunbaetour.domain.festival.repository.FestivalQueryRepository;
import com.chunbaetour.domain.festival.type.FestivalStatus;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CalendarServiceTest {

    @Mock private FestivalQueryRepository festivalQueryRepository;
    @Mock private CalendarService self;

    @InjectMocks private CalendarService calendarService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(calendarService, "self", self);
    }

    // ── getMonthlyCalendar ─────────────────────────────────────────────────

    @Test
    @DisplayName("getMonthlyCalendar — 6/18~6/23 축제: 18·19·20·21·22·23일 6일 모두 마커 표시")
    void getMonthlyCalendar_6월18일부터_23일까지_6일_모두_표시() {
        // 6/18 시작 ~ 6/23 종료 축제 → 해당 날짜 포함 여부 검증
        LocalDate start = LocalDate.of(2026, 6, 18);
        LocalDate end   = LocalDate.of(2026, 6, 23);
        given(festivalQueryRepository.findActiveInMonth(2026, 6)).willReturn(List.of(buildFestival(1L, start, end)));

        CalendarResponse response = calendarService.getMonthlyCalendar(2026, 6);

        assertThat(response.year()).isEqualTo(2026);
        assertThat(response.month()).isEqualTo(6);
        // 6/18 ~ 6/23 사이 날짜 6개 모두 마커 존재
        assertThat(response.markedDates()).containsExactlyInAnyOrder(
                LocalDate.of(2026, 6, 18),
                LocalDate.of(2026, 6, 19),
                LocalDate.of(2026, 6, 20),
                LocalDate.of(2026, 6, 21),
                LocalDate.of(2026, 6, 22),
                LocalDate.of(2026, 6, 23)
        );
        // 각 날짜에 이 축제 1개씩 포함
        assertThat(response.events().get(LocalDate.of(2026, 6, 18))).hasSize(1);
        assertThat(response.events().get(LocalDate.of(2026, 6, 20))).hasSize(1);
        assertThat(response.events().get(LocalDate.of(2026, 6, 23))).hasSize(1);
    }

    @Test
    @DisplayName("getMonthlyCalendar — 축제 없으면 빈 markedDates와 빈 events 반환")
    void getMonthlyCalendar_빈_결과() {
        given(festivalQueryRepository.findActiveInMonth(2026, 6)).willReturn(List.of());

        CalendarResponse response = calendarService.getMonthlyCalendar(2026, 6);

        assertThat(response.markedDates()).isEmpty();
        assertThat(response.events()).isEmpty();
    }

    @Test
    @DisplayName("getMonthlyCalendar — 축제가 월 앞쪽 경계를 넘으면 월 첫날부터 클리핑")
    void getMonthlyCalendar_월_앞쪽_경계_클리핑() {
        // 5/25 시작 ~ 6/5 종료 → 6월 구간에서는 6/1~6/5만 마커
        LocalDate start = LocalDate.of(2026, 5, 25);
        LocalDate end   = LocalDate.of(2026, 6, 5);
        given(festivalQueryRepository.findActiveInMonth(2026, 6)).willReturn(List.of(buildFestival(1L, start, end)));

        CalendarResponse response = calendarService.getMonthlyCalendar(2026, 6);

        assertThat(response.markedDates()).hasSize(5); // 6/1~6/5
        assertThat(response.markedDates()).contains(LocalDate.of(2026, 6, 1));
        assertThat(response.markedDates()).contains(LocalDate.of(2026, 6, 5));
        assertThat(response.markedDates()).doesNotContain(LocalDate.of(2026, 5, 31));
    }

    @Test
    @DisplayName("getMonthlyCalendar — 축제가 월 뒤쪽 경계를 넘으면 월 마지막날까지 클리핑")
    void getMonthlyCalendar_월_뒤쪽_경계_클리핑() {
        // 6/28 시작 ~ 7/5 종료 → 6월 구간에서는 6/28~6/30만 마커
        LocalDate start = LocalDate.of(2026, 6, 28);
        LocalDate end   = LocalDate.of(2026, 7, 5);
        given(festivalQueryRepository.findActiveInMonth(2026, 6)).willReturn(List.of(buildFestival(1L, start, end)));

        CalendarResponse response = calendarService.getMonthlyCalendar(2026, 6);

        assertThat(response.markedDates()).hasSize(3); // 6/28~6/30
        assertThat(response.markedDates()).contains(LocalDate.of(2026, 6, 28));
        assertThat(response.markedDates()).contains(LocalDate.of(2026, 6, 30));
        assertThat(response.markedDates()).doesNotContain(LocalDate.of(2026, 7, 1));
    }

    @Test
    @DisplayName("getMonthlyCalendar — 동일 날짜에 두 축제가 있으면 해당 날짜 이벤트 count 2")
    void getMonthlyCalendar_동일_날짜_두_축제_count_2() {
        LocalDate day = LocalDate.of(2026, 6, 15);
        Festival f1 = buildFestival(1L, day, day);
        Festival f2 = buildFestival(2L, day, day);
        given(festivalQueryRepository.findActiveInMonth(2026, 6)).willReturn(List.of(f1, f2));

        CalendarResponse response = calendarService.getMonthlyCalendar(2026, 6);

        assertThat(response.events().get(day)).hasSize(2);
    }

    // ── getDailyCalendar ──────────────────────────────────────────────────

    @Test
    @DisplayName("getDailyCalendar — 해당 날짜 이벤트 목록 정상 반환")
    void getDailyCalendar_성공() {
        LocalDate date = LocalDate.of(2026, 6, 20);
        FestivalCacheData cacheData = buildCacheData(1L,
                LocalDate.of(2026, 6, 18), LocalDate.of(2026, 6, 23));
        given(self.findCachedDailyFestivals(date)).willReturn(List.of(cacheData));

        DailyCalendarResponse response = calendarService.getDailyCalendar(date);

        assertThat(response.date()).isEqualTo(date);
        assertThat(response.events()).hasSize(1);
        assertThat(response.events().get(0).festivalId()).isEqualTo(1L);
        assertThat(response.events().get(0).name()).isEqualTo("축제1");
    }

    @Test
    @DisplayName("getDailyCalendar — 해당 날짜 축제 없으면 빈 events 반환")
    void getDailyCalendar_빈_결과() {
        LocalDate date = LocalDate.of(2026, 6, 20);
        given(self.findCachedDailyFestivals(date)).willReturn(List.of());

        DailyCalendarResponse response = calendarService.getDailyCalendar(date);

        assertThat(response.date()).isEqualTo(date);
        assertThat(response.events()).isEmpty();
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private Festival buildFestival(Long id, LocalDate start, LocalDate end) {
        Festival f = Festival.create("축제" + id, null, "서울", "서울시", start, end, null, null, FestivalStatus.ACTIVE);
        ReflectionTestUtils.setField(f, "id", id);
        return f;
    }

    private FestivalCacheData buildCacheData(Long id, LocalDate start, LocalDate end) {
        return new FestivalCacheData(id, "축제" + id, null, "서울", "서울시", start, end, null, null, FestivalStatus.ACTIVE);
    }
}
