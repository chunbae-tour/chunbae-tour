package com.chunbaetour.domain.festival.type;

import java.time.LocalDate;

/**
 * 축제 진행 상태 타입.
 * <p>
 * {@link #of(LocalDate, LocalDate, LocalDate)}를 통해 오늘 날짜 기준 동적 계산한다.
 * DB 스키마에 {@code NOT NULL} 제약이 있더라도, JPA 레이어 위에서 null이 전달될 수 있으므로
 * 메서드 내부에서 null 가드를 수행한다.
 * </p>
 */
public enum FestivalProgressStatus {
    UPCOMING,     // 예정
    IN_PROGRESS,  // 진행 중
    ENDED;        // 종료

    /**
     * 오늘 날짜 기준으로 축제 진행 상태를 계산한다.
     *
     * @param startDate 축제 시작일 (non-null)
     * @param endDate   축제 종료일 (non-null)
     * @param today     오늘 날짜 (non-null)
     * @return 계산된 진행 상태
     * @throws IllegalArgumentException startDate 또는 endDate가 null인 경우
     */
    public static FestivalProgressStatus of(LocalDate startDate, LocalDate endDate, LocalDate today) {
        // [BUG FIX] DB nullable=false 제약이 있어도 JPA 영속성 컨텍스트 바깥(테스트 픽스처,
        // 직접 생성 등)에서 null이 넘어오면 today.isBefore(startDate) → NPE 발생.
        // 계약 위반임을 명확히 하기 위해 IllegalArgumentException을 던진다.
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException(
                    "Festival startDate/endDate must not be null. startDate=" + startDate + ", endDate=" + endDate);
        }
        if (today.isBefore(startDate)) {
            return UPCOMING;
        } else if (today.isAfter(endDate)) {
            return ENDED;
        }
        return IN_PROGRESS;
    }
}
