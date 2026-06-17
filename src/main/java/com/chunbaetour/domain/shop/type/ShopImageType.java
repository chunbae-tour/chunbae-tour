package com.chunbaetour.domain.shop.type;

/**
 * 가게 사진 용도 구분 (KAN-315, ADR-003).
 *
 * <ul>
 *   <li>{@link #PROFILE} — 가게 대표 사진. 가게당 1장(앱 레벨 보장). 신규 업로드 시 기존 PROFILE 교체.</li>
 *   <li>{@link #GALLERY} — 소개(갤러리) 사진. 다중 허용, {@code sort_order}로 정렬.</li>
 * </ul>
 *
 * <p>메뉴 사진은 {@code Menu.imageUrl}로 이미 존재해 미포함, 공지 사진은 보류(공지 API 확장 시).
 */
public enum ShopImageType {
    PROFILE,
    GALLERY
}
