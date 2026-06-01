package com.chunbaetour.domain.admin.audit;

/**
 * 운영 액션 1건의 기록 컨텍스트 (KAN-179, Admin Epic KAN-177 S01).
 *
 * <p>컨트롤러 메서드 시점에 모인 정보를 묶어 {@link AdminActionLogService#record}에 전달하는 단일 record.
 * AOP advice가 {@link LogAdminAction} 어노테이션에서 actionType/targetType을, 메서드 시그니처/리턴값에서
 * targetId/beforeStatus/afterStatus를 추출해 본 record를 만든다.
 *
 * <p>필드 정책
 * <ul>
 *   <li>{@code adminUserId} / {@code actionType} / {@code targetType} / {@code targetId} — 필수.
 *       null 시 entity 생성 단계에서 {@link IllegalArgumentException}.</li>
 *   <li>{@code reason} — 본 슬라이스는 항상 null. 후속 슬라이스에서 request body 보강 시 채움.</li>
 *   <li>{@code beforeStatus} / {@code afterStatus} — 상태 전이를 추적해야 하는 액션(예: USER_SUSPEND)만 채움.</li>
 * </ul>
 */
public record AdminActionContext(
        Long adminUserId,
        AdminActionType actionType,
        AdminTargetType targetType,
        Long targetId,
        String reason,
        String beforeStatus,
        String afterStatus) {
}
