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
import jakarta.persistence.Version;
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

    /** 낙관적 락 — 관리자 동시 처리 중복 방지 (KAN-92). */
    @Version
    private Long version;

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

    /** 신고 대상 사용자 ID — 도메인별 제재 누적 집계용 (PR1+PR2). 콘텐츠 신고 시 작성자 ID. */
    @Column(name = "reported_user_id")
    private Long reportedUserId;

    public static Report create(Long reporterId, ReportTargetType targetType, Long targetId,
                                ReportReason reason, String description, Long reportedUserId) {
        Report report = new Report();
        report.reporterId = reporterId;
        report.targetType = targetType;
        report.targetId = targetId;
        report.reason = reason;
        report.description = description;
        report.status = ReportStatus.PENDING;
        report.reportedUserId = reportedUserId;
        return report;
    }

    /**
     * 신고 처리 (WARNING / SUSPEND / DELETE / HIDE_SHOP / REVOKE_MERCHANT).
     * status = RESOLVED, action·adminNote·resolvedBy·resolvedAt 기록.
     * DISMISS는 {@link #dismiss} 전용 메서드로 분리.
     *
     * <p>도메인 불변식 보호: PENDING 상태가 아니면 IllegalStateException.
     * 서비스 레이어에서도 체크하지만 엔티티 자체도 방어.
     *
     * <p>resolvedAt은 서비스가 주입한 시각({@code now(clock)}, UTC)을 받는다 —
     * 시스템 전역 시간 기준을 통일해 제재 집계·만료 비교의 정합성을 보장한다.
     */
    public void resolve(ReportAction action, String adminNote, String resolvedBy, LocalDateTime resolvedAt) {
        if (!isPending()) {
            throw new IllegalStateException("이미 처리된 신고입니다. reportId=" + this.id);
        }
        if (action == null) {
            throw new IllegalArgumentException("action은 null일 수 없습니다.");
        }
        this.status = ReportStatus.RESOLVED;
        this.action = action;
        this.adminNote = adminNote;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = resolvedAt;
    }

    /**
     * 신고 무시 (DISMISS).
     * status = DISMISSED로 종결.
     *
     * <p>도메인 불변식 보호: 서비스 레이어에서 isPending() 체크를 하더라도
     * 엔티티 자체도 방어 — 잘못된 직접 호출 시 상태 덮어쓰기 방지.
     */
    public void dismiss(String adminNote, String resolvedBy, LocalDateTime resolvedAt) {
        if (!isPending()) {
            throw new IllegalStateException("이미 처리된 신고입니다. reportId=" + this.id);
        }
        this.status = ReportStatus.DISMISSED;
        this.action = ReportAction.DISMISS;
        this.adminNote = adminNote;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = resolvedAt;
    }

    public boolean isPending() {
        return this.status == ReportStatus.PENDING;
    }

    public boolean isResolved() {
        return this.status == ReportStatus.RESOLVED;
    }

    /**
     * 관리자 오판 정정 — RESOLVED 신고를 DISMISSED로 되돌린다.
     * 콘텐츠 복원은 서비스 레이어에서 {@code ReportContentActionEvent(RESTORE)}로 별도 처리.
     *
     * <p>도메인 불변식: RESOLVED 상태에서만 호출 가능 (서비스에서도 검증).
     * <p>{@code action}은 원래 취한 조치(DELETE/SUSPEND 등) 그대로 보존한다 —
     * 정정 여부는 {@code status=DISMISSED}와 adminNote로 표현하고, action 필드는 오염시키지 않는다.
     */
    public void correctToDismissed(String adminNote, String correctedBy, LocalDateTime correctedAt) {
        if (!isResolved()) {
            throw new IllegalStateException("RESOLVED 신고만 정정할 수 있습니다. reportId=" + this.id);
        }
        this.status = ReportStatus.DISMISSED;
        this.adminNote = adminNote;
        this.resolvedBy = correctedBy;
        this.resolvedAt = correctedAt;
    }
}
