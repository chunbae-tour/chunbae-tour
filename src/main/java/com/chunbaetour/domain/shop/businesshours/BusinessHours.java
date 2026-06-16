package com.chunbaetour.domain.shop.businesshours;

import com.chunbaetour.domain.shop.type.BusinessStatus;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 운영시간 문자열(operatingHours)로 현재 영업여부를 계산하는 값 객체 (B7 MVP, KAN-301).
 *
 * <p><b>MVP 범위:</b> 문자열에서 첫 번째 {@code HH:mm-HH:mm}(구분자 - 또는 ~) 시간 범위만 추출해 판정한다.
 * 요일·휴무일·복수 시간대는 해석하지 않는다 — 예: {@code "월~금 09:00-22:00"}은 요일을 무시하고
 * 09:00-22:00 범위로만 본다. 시간 범위를 못 찾으면 {@link BusinessStatus#UNKNOWN}.
 *
 * <p><b>자정 걸침</b>(예: {@code 22:00-02:00}): start > end면 {@code [start,24:00) ∪ [00:00,end)}로 해석.
 * <b>경계</b>: 시작 시각 포함(OPEN), 종료 시각 제외(CLOSED). open==close는 24시간 영업으로 본다.
 *
 * <p>순수 함수 — 현재시각을 인자로 받아 Clock 의존 없이 단위 테스트 가능. 호출자가 KST 기준 now를 넘긴다.
 */
public final class BusinessHours {

    private BusinessHours() {}

    // "9:00-18:00", "09:00 ~ 22:00" 등 — 구분자 -/~, 주변 공백 허용
    private static final Pattern TIME_RANGE = Pattern.compile(
            "(\\d{1,2}):(\\d{2})\\s*[-~]\\s*(\\d{1,2}):(\\d{2})");

    /**
     * @param operatingHours 운영시간 문자열(자유 텍스트, null 허용)
     * @param now            판정 기준 현재시각 (KST 기준으로 호출자가 전달)
     * @return OPEN / CLOSED / UNKNOWN
     */
    public static BusinessStatus statusAt(String operatingHours, LocalDateTime now) {
        if (operatingHours == null || operatingHours.isBlank()) {
            return BusinessStatus.UNKNOWN;
        }
        Matcher m = TIME_RANGE.matcher(operatingHours);
        if (!m.find()) {
            return BusinessStatus.UNKNOWN;
        }
        LocalTime open;
        LocalTime close;
        try {
            open = LocalTime.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
            close = LocalTime.of(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)));
        } catch (NumberFormatException | DateTimeException e) {
            // "25:00" 등 비정상 시각 — 판정 불가
            return BusinessStatus.UNKNOWN;
        }
        return isOpen(open, close, now.toLocalTime()) ? BusinessStatus.OPEN : BusinessStatus.CLOSED;
    }

    private static boolean isOpen(LocalTime open, LocalTime close, LocalTime t) {
        if (open.equals(close)) {
            // 동일 시각 — 24시간 영업으로 해석
            return true;
        }
        if (open.isBefore(close)) {
            // 일반: [open, close)
            return !t.isBefore(open) && t.isBefore(close);
        }
        // 자정 걸침: [open, 24:00) ∪ [00:00, close)
        return !t.isBefore(open) || t.isBefore(close);
    }
}
