package com.chunbaetour.domain.report.dto.response;

import com.chunbaetour.domain.report.entity.Report;
import com.chunbaetour.domain.report.entity.ReportReason;
import com.chunbaetour.domain.report.entity.ReportStatus;
import com.chunbaetour.domain.report.entity.ReportTargetType;
import com.chunbaetour.domain.report.type.ReportAction;
import java.time.LocalDateTime;

/**
 * 관리자 신고 단건 상세 응답 (KAN-91).
 * 목록 응답({@link ReportResponse})에 reporterId·targetContent 추가.
 *
 * <p>명세 기본 필드 외에 {@code action}, {@code adminNote}, {@code resolvedBy}, {@code resolvedAt}
 * 포함 — 처리 완료 신고 조회 시 관리자 UI에서 필요. 프론트 계약 시 반영 필요.
 */
public record ReportDetailResponse(
        Long reportId,
        ReportTargetType targetType,
        Long targetId,
        ReportReason reason,
        String description,
        ReportStatus status,
        ReportAction action,
        String reporterNickname,
        Long reporterId,
        String targetContent,
        String adminNote,
        String resolvedBy,
        LocalDateTime resolvedAt,
        LocalDateTime createdAt
) {
    public static ReportDetailResponse of(Report report, String reporterNickname, String targetContent) {
        return new ReportDetailResponse(
                report.getId(),
                report.getTargetType(),
                report.getTargetId(),
                report.getReason(),
                report.getDescription(),
                report.getStatus(),
                report.getAction(),
                reporterNickname,
                report.getReporterId(),
                targetContent,
                report.getAdminNote(),
                report.getResolvedBy(),
                report.getResolvedAt(),
                report.getCreatedAt()
        );
    }
}
