package com.chunbaetour.domain.store.type;

/**
 * 스토어 상품(아이템)의 사용처(redemption) 범위.
 *
 * <p>QR 사용 처리({@code UserItemService.useByQr}) 시 "이 아이템을 이 검증자/가게에서 사용할 수 있는가"를
 * 판단하는 기준이다. 현재 MVP의 스토어 상품은 전부 {@link #GLOBAL}(전 가맹점 공통 교환권)이며,
 * 가게/파트너/행사/장소 한정 사용처는 추후 redemption 도메인 확장에서 도입한다.
 *
 * <p>설계 배경: {@code hanes/미구현API.md} "Redemption 도메인으로 확장" 참조.
 */
public enum RedemptionScope {
    /** 특정 가게(shop)에서만 사용 가능 — 추후 product↔shop 매핑 검증 도입 예정 */
    SHOP_ONLY,
    /** 특정 파트너 조직에서만 사용 가능 — item_redemption_permissions 매핑 예정 */
    PARTNER_ONLY,
    /** 특정 행사/야시장/부스에서만 사용 가능 */
    EVENT_ONLY,
    /** 특정 관광지/장소에서만 사용 가능 */
    PLACE_ONLY,
    /** 제휴 사용처 어디서나 사용 가능(전 가맹점 공통). 현재 모든 스토어 상품의 기본값. */
    GLOBAL
}
