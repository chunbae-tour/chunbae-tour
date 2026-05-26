package com.chunbaetour.domain.report.dto.response;

import com.chunbaetour.domain.report.entity.Report;
import com.chunbaetour.domain.report.type.ReportAction;
import com.chunbaetour.domain.report.type.ReportReason;
import com.chunbaetour.domain.report.type.ReportStatus;
import com.chunbaetour.domain.report.type.ReportTargetType;
import java.time.LocalDateTime;

public record ReportResponse(
        Long reportId,
        ReportTargetType targetType,
        Long targetId,
        ReportReason reason,
        String description,
        ReportStatus status,
        ReportAction action,
        String reporterNickname,
        String adminNote,
        String resolvedBy,
        LocalDateTime resolvedAt,
        LocalDateTime createdAt
) {
    public static ReportResponse of(Report report, String reporterNickname) {
        return new ReportResponse(
                report.getId(),
                report.getTargetType(),
                report.getTargetId(),
                report.getReason(),
                report.getDescription(),
                report.getStatus(),
                report.getAction(),
                reporterNickname,
                report.getAdminNote(),
                report.getResolvedBy(),
                report.getResolvedAt(),
                report.getCreatedAt()
        );
    }
}
