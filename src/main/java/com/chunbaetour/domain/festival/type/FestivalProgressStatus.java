package com.chunbaetour.domain.festival.type;

public enum FestivalProgressStatus {
    UPCOMING,     // 예정
    IN_PROGRESS,  // 진행 중
    ENDED;        // 종료

    public static FestivalProgressStatus of(java.time.LocalDate startDate, java.time.LocalDate endDate, java.time.LocalDate today) {
        if (today.isBefore(startDate)) {
            return UPCOMING;
        } else if (today.isAfter(endDate)) {
            return ENDED;
        }
        return IN_PROGRESS;
    }
}
