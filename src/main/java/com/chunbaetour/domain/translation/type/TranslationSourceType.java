package com.chunbaetour.domain.translation.type;

// 번역 요청 출처 도메인 — 정적(관리자/소수 작성, 다수 유저 동일 문구 반복 조회) vs 동적(유저별 1회성 작성) 분류
public enum TranslationSourceType {
    // 정적이지만 entity-ID 기반 번역 endpoint 도입 전까지는 캐시 미적용 (client content 직접 캐싱 방지)
    FESTIVAL(false),
    ATTRACTION(false),
    MARKET(false),
    // FAQ — entity-ID 기반 endpoint(/api/v1/faqs/{id}/translation)에서만 캐시 적용. 범용 번역 endpoint는 sourceType=FAQ 요청 거부
    FAQ(true),
    NOTICE(false),
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
