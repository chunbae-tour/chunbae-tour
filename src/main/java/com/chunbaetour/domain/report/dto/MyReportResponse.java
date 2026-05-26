package com.chunbaetour.domain.report.dto;

import com.chunbaetour.domain.report.entity.Report;
import com.chunbaetour.domain.report.entity.ReportReason;
import com.chunbaetour.domain.report.entity.ReportStatus;
import com.chunbaetour.domain.report.entity.ReportTargetType;
import java.time.LocalDateTime;

/**
 * 사용자 신고 내역 조회 응답 DTO.
 * 관리자 전용 필드(reporterNickname, adminNote 등)는 포함하지 않는다.
 */
public record MyReportResponse(
        Long reportId,
        ReportTargetType targetType,
        Long targetId,
        ReportReason reason,
        String description,
        ReportStatus status,
        LocalDateTime createdAt
) {
    public static MyReportResponse of(Report report) {
        return new MyReportResponse(
                report.getId(),
                report.getTargetType(),
                report.getTargetId(),
                report.getReason(),
                report.getDescription(),
                report.getStatus(),
                report.getCreatedAt()
        );
    }
}
