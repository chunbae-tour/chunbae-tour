package com.chunbaetour.domain.report.dto.response;

import com.chunbaetour.domain.report.entity.Report;
import com.chunbaetour.domain.report.entity.ReportReason;
import com.chunbaetour.domain.report.entity.ReportStatus;
import com.chunbaetour.domain.report.entity.ReportTargetType;
import com.chunbaetour.domain.report.type.ReportAction;
import java.time.LocalDateTime;

/**
 * 관리자 신고 목록 조회 응답 DTO (KAN-91).
 * 신고 단건 상세는 {@link ReportDetailResponse} 참조.
 */
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
