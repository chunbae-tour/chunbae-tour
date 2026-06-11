package com.chunbaetour.domain.report.entity;

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
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "user_sanction")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class UserSanction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private ReportTargetType targetType;

    @Enumerated(EnumType.STRING)
    @Column(name = "sanction_type", nullable = false, length = 20)
    private SanctionType sanctionType;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    /** PERMANENT이면 null. */
    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    /** 기간 만료 또는 관리자 조기 해제 시각. 아직 진행 중이면 null. */
    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    /** 관리자 조기 해제 시 해제한 관리자 ID. 자동 해제이면 null. */
    @Column(name = "released_by")
    private Long releasedBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static UserSanction create(
            Long userId,
            Long reportId,
            ReportTargetType targetType,
            SanctionType sanctionType,
            String reason,
            LocalDateTime startedAt,
            LocalDateTime endedAt) {
        UserSanction sanction = new UserSanction();
        sanction.userId = userId;
        sanction.reportId = reportId;
        sanction.targetType = targetType;
        sanction.sanctionType = sanctionType;
        sanction.reason = reason;
        sanction.startedAt = startedAt;
        sanction.endedAt = endedAt;
        return sanction;
    }

    /**
     * 관리자 조기 해제.
     * releasedAt·releasedBy 기록. 이미 해제된 이력은 불변이므로 중복 호출 방어.
     */
    public void release(Long adminId, LocalDateTime releasedAt) {
        if (this.releasedAt != null) {
            return;
        }
        this.releasedAt = releasedAt;
        this.releasedBy = adminId;
    }
}
