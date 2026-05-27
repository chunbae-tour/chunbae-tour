package com.chunbaetour.domain.report.dto.response;

import com.chunbaetour.domain.report.entity.Report;
import com.chunbaetour.domain.report.entity.ReportStatus;
import java.time.LocalDateTime;

/**
 * 신고 처리 결과 응답 DTO (KAN-92).
 * POST /admin/reports/{id}/resolve 및 /resolve/merchant 공용.
 */
public record ReportResolveResponse(
        Long reportId,
        ReportStatus status,
        String action,
        String adminNote,
        LocalDateTime resolvedAt,
        String resolvedBy
) {
    public static ReportResolveResponse of(Report report) {
        return new ReportResolveResponse(
                report.getId(),
                report.getStatus(),
                report.getAction() != null ? report.getAction().name() : null,
                report.getAdminNote(),
                report.getResolvedAt(),
                report.getResolvedBy()
        );
    }
}
