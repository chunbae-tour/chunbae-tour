package com.chunbaetour.domain.shop.businesshours;

import static org.assertj.core.api.Assertions.assertThat;

import com.chunbaetour.domain.shop.type.BusinessStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BusinessHoursTest {

    /** 날짜는 판정에 무관(MVP는 요일 미해석) — 시각만 의미. */
    private LocalDateTime at(int hour, int minute) {
        return LocalDateTime.of(2026, 6, 15, hour, minute);
    }

    @Test
    @DisplayName("일반 운영시간 09:00-18:00 — 영업시간 내 OPEN")
    void open_withinHours() {
        assertThat(BusinessHours.statusAt("09:00-18:00", at(14, 0))).isEqualTo(BusinessStatus.OPEN);
    }

    @Test
    @DisplayName("일반 운영시간 09:00-18:00 — 영업시간 외 CLOSED")
    void closed_outsideHours() {
        assertThat(BusinessHours.statusAt("09:00-18:00", at(20, 0))).isEqualTo(BusinessStatus.CLOSED);
        assertThat(BusinessHours.statusAt("09:00-18:00", at(8, 59))).isEqualTo(BusinessStatus.CLOSED);
    }

    @Test
    @DisplayName("경계 — 시작 시각 포함(OPEN), 종료 시각 제외(CLOSED)")
    void boundary_startInclusive_endExclusive() {
        assertThat(BusinessHours.statusAt("09:00-18:00", at(9, 0))).isEqualTo(BusinessStatus.OPEN);
        assertThat(BusinessHours.statusAt("09:00-18:00", at(18, 0))).isEqualTo(BusinessStatus.CLOSED);
    }

    @Test
    @DisplayName("요일 접두 텍스트는 무시하고 시간 범위만 추출 — '월~금 09:00-22:00'")
    void ignoresDayPrefix_extractsTimeRange() {
        assertThat(BusinessHours.statusAt("월~금 09:00-22:00", at(10, 0))).isEqualTo(BusinessStatus.OPEN);
        assertThat(BusinessHours.statusAt("월~금 09:00-22:00", at(23, 0))).isEqualTo(BusinessStatus.CLOSED);
    }

    @Test
    @DisplayName("물결 구분자 '09:00 ~ 21:00' 도 파싱")
    void parsesTildeSeparator() {
        assertThat(BusinessHours.statusAt("매일 09:00 ~ 21:00", at(12, 0))).isEqualTo(BusinessStatus.OPEN);
    }

    @Test
    @DisplayName("자정 걸침 22:00-02:00 — 23:00·01:00 OPEN, 03:00 CLOSED")
    void midnightCross() {
        assertThat(BusinessHours.statusAt("22:00-02:00", at(23, 0))).isEqualTo(BusinessStatus.OPEN);
        assertThat(BusinessHours.statusAt("22:00-02:00", at(1, 0))).isEqualTo(BusinessStatus.OPEN);
        assertThat(BusinessHours.statusAt("22:00-02:00", at(3, 0))).isEqualTo(BusinessStatus.CLOSED);
    }

    @Test
    @DisplayName("24시간 영업 — open==close 는 모든 시각 OPEN (open.equals(close) 분기 회귀 가드)")
    void open_24h_whenOpenEqualsClose() {
        // 00:00-00:00 → 자정 경계 무관 항상 영업
        assertThat(BusinessHours.statusAt("00:00-00:00", at(0, 0))).isEqualTo(BusinessStatus.OPEN);
        assertThat(BusinessHours.statusAt("00:00-00:00", at(12, 0))).isEqualTo(BusinessStatus.OPEN);
        assertThat(BusinessHours.statusAt("00:00-00:00", at(23, 59))).isEqualTo(BusinessStatus.OPEN);
        // 00:00 외 동일 시각도 24시간 영업으로 해석 (09:00-09:00)
        assertThat(BusinessHours.statusAt("09:00-09:00", at(3, 0))).isEqualTo(BusinessStatus.OPEN);
    }

    @Test
    @DisplayName("정보 없음/파싱 불가 — null·blank·연중무휴·휴무 → UNKNOWN")
    void unknown_whenUnparseable() {
        assertThat(BusinessHours.statusAt(null, at(12, 0))).isEqualTo(BusinessStatus.UNKNOWN);
        assertThat(BusinessHours.statusAt("", at(12, 0))).isEqualTo(BusinessStatus.UNKNOWN);
        assertThat(BusinessHours.statusAt("   ", at(12, 0))).isEqualTo(BusinessStatus.UNKNOWN);
        assertThat(BusinessHours.statusAt("연중무휴", at(12, 0))).isEqualTo(BusinessStatus.UNKNOWN);
        assertThat(BusinessHours.statusAt("매주 일요일 휴무", at(12, 0))).isEqualTo(BusinessStatus.UNKNOWN);
    }

    @Test
    @DisplayName("비정상 시각 25:00-30:00 → UNKNOWN (예외 아님)")
    void unknown_whenInvalidTime() {
        assertThat(BusinessHours.statusAt("25:00-30:00", at(12, 0))).isEqualTo(BusinessStatus.UNKNOWN);
    }
}
