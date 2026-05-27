package com.chunbaetour.domain.report.entity;

import com.chunbaetour.domain.common.entity.BaseEntity;
import com.chunbaetour.domain.report.type.ReportAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "reports",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_reports_reporter_target",
                        columnNames = {"reporter_id", "target_type", "target_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private ReportTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportReason reason;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ReportAction action;

    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;

    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    public static Report create(Long reporterId, ReportTargetType targetType, Long targetId,
                                ReportReason reason, String description) {
        Report report = new Report();
        report.reporterId = reporterId;
        report.targetType = targetType;
        report.targetId = targetId;
        report.reason = reason;
        report.description = description;
        report.status = ReportStatus.PENDING;
        return report;
    }

    /**
     * 신고 처리 (WARNING / SUSPEND / DELETE).
     * status = RESOLVED, action·adminNote·resolvedBy·resolvedAt 기록.
     *
     * <p>TODO(Clock): LocalDateTime.now() 직접 사용 — 테스트에서 시간 제어 불가.
     * 추후 Clock 주입 방식으로 개선 가능 (엔티티 Clock 주입은 복잡도 증가로 MVP 범위 제외).
     */
    public void resolve(ReportAction action, String adminNote, String resolvedBy) {
        if (action == null) {
            throw new IllegalArgumentException("action은 null일 수 없습니다.");
        }
        this.status = ReportStatus.RESOLVED;
        this.action = action;
        this.adminNote = adminNote;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = LocalDateTime.now();
    }

    /**
     * 신고 무시 (DISMISS).
     * status = DISMISSED로 종결.
     */
    public void dismiss(String adminNote, String resolvedBy) {
        this.status = ReportStatus.DISMISSED;
        this.action = ReportAction.DISMISS;
        this.adminNote = adminNote;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = LocalDateTime.now();
    }

    public boolean isPending() {
        return this.status == ReportStatus.PENDING;
    }
}
