package com.chunbaetour.domain.admin.audit;

/**
 * 운영 액션 대상 도메인 enum (KAN-179, Admin Epic KAN-177 S01).
 *
 * <p>PRD §"타겟 enum" 카탈로그 전체를 본 슬라이스에서 한 번에 정의한다. 후속 슬라이스가 새 타겟을 추가하면
 * 이 enum에도 항목을 더하고 {@code docs/operations/admin-action-log-catalog.md}를 함께 갱신해야 한다.
 *
 * <p>명칭 정합 — baseline 도메인이 {@code Festival}이므로 {@link #FESTIVAL} 사용 (PRD에 EVENT로 표기된 부분은
 * 코더 A 지시문에서 정정).
 */
public enum AdminTargetType {

    USER,
    MERCHANT,
    SHOP,
    SHOP_CERTIFICATION,
    MERCHANT_APPLICATION,
    AD_APPLICATION,
    REPORT,
    REFUND,
    PLACE,
    FESTIVAL,
    BANNER,
    FAQ,
    SUPPORT_ROOM;
}
