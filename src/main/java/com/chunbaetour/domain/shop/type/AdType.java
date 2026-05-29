package com.chunbaetour.domain.shop.type;

/**
 * 광고 노출 지면 타입.
 *
 * <p>현재 MVP는 일반 배너 광고만 지원한다.
 * TODO(KAN-162): 프론트 광고 지면 확정 후 MAIN_BANNER, COURSE_BANNER, FESTIVAL_BANNER 등으로 확장한다.
 */
public enum AdType {

    BANNER(1_000L);

    private final long dailyPrice;

    AdType(long dailyPrice) {
        this.dailyPrice = dailyPrice;
    }

    /** 광고 기간에 따른 신청 비용을 서버 정책으로 계산한다. */
    public long calculateCost(long durationDays) {
        return Math.multiplyExact(dailyPrice, durationDays);
    }
}
