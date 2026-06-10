package com.chunbaetour.domain.translation.type;

// 번역 요청 출처 도메인 — 정적(관리자/소수 작성, 다수 유저 동일 문구 반복 조회) vs 동적(유저별 1회성 작성) 분류
public enum TranslationSourceType {
    // 정적 — DB(translation_cache) + Redis 캐시 적용
    FESTIVAL(true),
    ATTRACTION(true),
    MARKET(true),
    FAQ(true),
    NOTICE(true),
    // 동적 — 캐시 없이 매번 Google API 호출
    CHAT(false),
    POST(false),
    REVIEW(false),
    SUPPORT(false);

    private final boolean cacheable;

    TranslationSourceType(boolean cacheable) {
        this.cacheable = cacheable;
    }

    public boolean isCacheable() {
        return cacheable;
    }
}
