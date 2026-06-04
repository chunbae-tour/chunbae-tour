package com.chunbaetour.domain.admin.audit;

/**
 * 운영 액션 종류 (KAN-179, Admin Epic KAN-177 S01).
 *
 * <p>본 슬라이스는 인프라만 제공하므로 enum은 최소(2개)로 시작한다. 후속 슬라이스가 자신의 액션을 추가할 때
 * 이 enum에 항목을 더하고 {@code docs/operations/admin-action-log-catalog.md}를 함께 갱신해야 한다.
 *
 * <p>본 슬라이스: {@code USER_SUSPEND} / {@code USER_UNSUSPEND} 2개만. 후속 슬라이스에서
 * {@code SHOP_*}, {@code CERTIFICATION_*}, {@code PLACE_*}, {@code FESTIVAL_*}, {@code BANNER_*},
 * {@code FAQ_*}, {@code SUPPORT_*} 등 추가 예정. 추가 시
 * {@code docs/operations/admin-action-log-catalog.md} 동시 갱신 의무.
 *
 * <p>S02 머지 시 wiring 대상: {@link #USER_SUSPEND} / {@link #USER_UNSUSPEND}.
 *
 * <p>VARCHAR(64) 컬럼에 {@code @Enumerated(EnumType.STRING)}으로 저장된다. enum 이름을 그대로 DB에 쓰므로
 * 이름 변경 시 기존 row 호환성 검토 필요(별도 마이그레이션).
 */
public enum AdminActionType {

    /** S02 사용자 정지. */
    USER_SUSPEND,

    /** S02 사용자 정지 해제. */
    USER_UNSUSPEND,

    /** S04 가게 관리 (수정/숨김/복구) — status·description·phone·operatingHours partial update. */
    SHOP_UPDATE,

    /** S05 인증 승인 (KAN-204) — Shop.markCertified() cascade. */
    CERTIFICATION_APPROVE,

    /** S05 인증 거절 (KAN-204) — rejectReason 기록. */
    CERTIFICATION_REJECT,

    /** S05 인증 취소 (KAN-204) — Shop.unmarkCertified() 회수. */
    CERTIFICATION_CANCEL,

    /** S07 관광지/전통시장 등록 — Place 신규 생성. */
    PLACE_CREATE,

    /** S07 관광지/전통시장 수정 — partial update(null-skip). */
    PLACE_UPDATE,

    /** S07 관광지/전통시장 삭제 — Place.delete()(→DELETED soft delete). */
    PLACE_DELETE,

    /** S08 축제 등록 — Festival 신규 생성(KAN-215, KAN-95 admin CRUD 감사 wiring). */
    FESTIVAL_CREATE,

    /** S08 축제 수정 — Festival.update() 전체 교체(PUT). */
    FESTIVAL_UPDATE,

    /** S08 축제 삭제 — Festival.delete()(→DELETED soft delete). */
    FESTIVAL_DELETE,

    /** 공공데이터 축제 즉시 수집 — 관리자 수동 트리거(POST /admin/festivals/fetch). */
    FESTIVAL_FETCH,

    /** S09 배너 등록 — Banner 신규 생성(KAN-216). */
    BANNER_CREATE,

    /** S09 배너 수정 — partial update(null-skip). */
    BANNER_UPDATE,

    /** S09 배너 삭제 — Banner.delete()(→DELETED soft delete). */
    BANNER_DELETE;
}
