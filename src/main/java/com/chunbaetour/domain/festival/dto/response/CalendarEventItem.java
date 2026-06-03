package com.chunbaetour.domain.festival.dto.response;

import com.chunbaetour.domain.festival.entity.Festival;

/**
 * 월별 캘린더 이벤트 항목 (KAN-96).
 * type은 항상 "FESTIVAL".
 */
public record CalendarEventItem(
        Long festivalId,
        String name,
        String address,
        String type
) {
    public static CalendarEventItem of(Festival f) {
        return new CalendarEventItem(f.getId(), f.getName(), f.getAddress(), "FESTIVAL");
    }
}
