package com.chunbaetour.domain.store.type;

/**
 * 스토어 상품 카테고리 (KAN-302).
 * 자유 문자열 대신 고정 enum으로 분류 정책 통일 — 사용 목적 기준 5종.
 * 한글 표시명은 displayName으로 보유, 응답은 {@code {code, label}}(ProductCategoryResponse)로 노출.
 */
public enum ProductCategory {
    ADMISSION_TICKET("입장권"),   // 관광지·박물관·축제 입장권
    TOUR_PASS("투어 패스"),       // 여러 관광지 통합 이용권
    EXPERIENCE("체험"),           // 투어·공방·문화 체험
    DISCOUNT_COUPON("할인·교환권"), // 가게 할인권·메뉴 교환권
    LOCAL_PRODUCT("지역 상품");    // 지역 특산품·기념품

    private final String displayName;

    ProductCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
