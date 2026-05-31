package com.chunbaetour.domain.admin.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 운영 액션 1건의 불변 기록 entity (KAN-179, Admin Epic KAN-177 S01).
 *
 * <p>설계 원칙
 * <ul>
 *   <li><b>append-only</b> — 한 번 저장된 row는 수정/삭제하지 않는다. setter 미제공. update 메서드 없음.</li>
 *   <li><b>self-contained created_at</b> — {@link com.chunbaetour.domain.common.entity.BaseEntity}는 상속하지 않는다.
 *       updated_at이 없는 append-only 모델이므로 자체 {@link CreatedDate}만 사용.</li>
 *   <li><b>VARCHAR(64) enum 컬럼</b> — JPA {@link Enumerated}(STRING). DB enum 타입 사용 X (운영 enum 항목 추가 시
 *       DDL 변경 회피).</li>
 * </ul>
 *
 * <p>Builder는 패키지 외부 노출용이 아니라 본 도메인 안에서만 사용 — entity 생성 책임은
 * {@link #from(AdminActionContext)} 정적 팩토리에 둔다. 필수 필드 검증을 한곳에 모아 호출 누락을 차단한다.
 */
@Entity
@Table(name = "admin_action_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class AdminActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_user_id", nullable = false)
    private Long adminUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 64)
    private AdminActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 64)
    private AdminTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "before_status", length = 64)
    private String beforeStatus;

    @Column(name = "after_status", length = 64)
    private String afterStatus;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private AdminActionLog(Long adminUserId,
                           AdminActionType actionType,
                           AdminTargetType targetType,
                           Long targetId,
                           String reason,
                           String beforeStatus,
                           String afterStatus) {
        // 필수 필드 검증을 builder에서 처리 — 컨텍스트가 어떤 경로로 들어와도 동일 가드.
        // null이면 IllegalArgumentException — 호출자(서비스/AOP)에서 조기에 잡혀 DB 적재 후 발견하는 사고 차단.
        if (adminUserId == null) {
            throw new IllegalArgumentException("adminUserId must not be null");
        }
        if (actionType == null) {
            throw new IllegalArgumentException("actionType must not be null");
        }
        if (targetType == null) {
            throw new IllegalArgumentException("targetType must not be null");
        }
        if (targetId == null) {
            throw new IllegalArgumentException("targetId must not be null");
        }
        this.adminUserId = adminUserId;
        this.actionType = actionType;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reason = reason;
        this.beforeStatus = beforeStatus;
        this.afterStatus = afterStatus;
    }

    /**
     * {@link AdminActionContext}에서 entity를 만든다.
     *
     * <p>호출 책임을 한곳에 둠으로써 {@code repository.save(AdminActionLog.from(ctx))} 1줄로 적재가 끝나도록
     * 한다. 필수 필드 검증은 builder가 위임받는다.
     */
    public static AdminActionLog from(AdminActionContext context) {
        return AdminActionLog.builder()
                .adminUserId(context.adminUserId())
                .actionType(context.actionType())
                .targetType(context.targetType())
                .targetId(context.targetId())
                .reason(context.reason())
                .beforeStatus(context.beforeStatus())
                .afterStatus(context.afterStatus())
                .build();
    }
}
