package com.chunbaetour.domain.admin.audit;

/**
 * 운영 액션 종류 (KAN-179, Admin Epic KAN-177 S01).
 *
 * <p>본 슬라이스는 인프라만 제공하므로 enum은 최소(2개)로 시작한다. 후속 슬라이스가 자신의 액션을 추가할 때
 * 이 enum에 항목을 더하고 {@code docs/operations/admin-action-log-catalog.md}를 함께 갱신해야 한다.
 *
 * <p>S02 머지 시 wiring 대상: {@link #USER_SUSPEND} / {@link #USER_UNSUSPEND}.
 * 다른 항목(예: SHOP_UPDATE, CERTIFICATION_APPROVE 등)은 해당 슬라이스가 도입.
 *
 * <p>VARCHAR(64) 컬럼에 {@code @Enumerated(EnumType.STRING)}으로 저장된다. enum 이름을 그대로 DB에 쓰므로
 * 이름 변경 시 기존 row 호환성 검토 필요(별도 마이그레이션).
 */
public enum AdminActionType {

    /** S02 사용자 정지. */
    USER_SUSPEND,

    /** S02 사용자 정지 해제. */
    USER_UNSUSPEND;
}
