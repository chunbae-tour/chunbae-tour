package com.chunbaetour.domain.auth;

public enum SanctionType {

    NONE(0),
    WARNING(1),
    SUSPEND_7D(2),
    SUSPEND_30D(3),
    PERMANENT(4);

    private final int severity;

    SanctionType(int severity) {
        this.severity = severity;
    }

    public int severity() {
        return severity;
    }

    public boolean isHigherThan(SanctionType other) {
        return this.severity > other.severity;
    }

    /** 누적 승인 건수 → 제재 단계 매핑 (임계치: 3 이상/5 이상/10 이상/15 이상). */
    public static SanctionType fromCount(long count) {
        if (count >= 15) return PERMANENT;
        if (count >= 10) return SUSPEND_30D;
        if (count >= 5)  return SUSPEND_7D;
        if (count >= 3)  return WARNING;
        return NONE;
    }
}
