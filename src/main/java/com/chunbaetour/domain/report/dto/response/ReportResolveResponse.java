package com.chunbaetour.domain.report.dto.response;

import com.chunbaetour.domain.report.entity.Report;
import com.chunbaetour.domain.report.entity.ReportStatus;
import com.chunbaetour.domain.report.type.ReportAction;
import java.time.LocalDateTime;

/**
 * 신고 처리 결과 응답 DTO (KAN-92).
 * POST /admin/reports/{id}/resolve 및 /resolve/merchant 공용.
 *
 * <p>action은 {@link ReportAction} 타입 그대로 직렬화 — {@link ReportDetailResponse}와 일관성 유지.
 * Jackson 기본 설정으로 enum name() 문자열로 직렬화됨.
 */
public record ReportResolveResponse(
        Long reportId,
        ReportStatus status,
        ReportAction action,
        String adminNote,
        LocalDateTime resolvedAt,
        String resolvedBy
) {
    public static ReportResolveResponse of(Report report) {
        return new ReportResolveResponse(
                report.getId(),
                report.getStatus(),
                report.getAction(),
                report.getAdminNote(),
                report.getResolvedAt(),
                report.getResolvedBy()
        );
    }
}
