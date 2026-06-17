package com.chunbaetour.domain.report.dto.response;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountStatus;
import com.chunbaetour.domain.report.entity.Report;
import com.chunbaetour.domain.report.entity.ReportReason;
import com.chunbaetour.domain.report.entity.ReportStatus;
import com.chunbaetour.domain.report.entity.ReportTargetType;
import com.chunbaetour.domain.report.entity.SanctionType;
import com.chunbaetour.domain.report.type.ReportAction;
import java.time.LocalDateTime;

/**
 * 관리자 신고 목록 조회 응답 DTO (KAN-91).
 * 신고 단건 상세는 {@link ReportDetailResponse} 참조.
 *
 * <p>{@code reportedUserStatus}·{@code reportedUserSanctionType}: 피신고 유저의 계정 레벨
 * 현재 제재 상태(뱃지용). 도메인별 활성 제재 상세는 상세 조회(getReport)에서 제공.
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
        LocalDateTime createdAt,
        AccountStatus reportedUserStatus,
        SanctionType reportedUserSanctionType
) {
    public static ReportResponse of(Report report, String reporterNickname, Account reportedUser) {
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
                report.getCreatedAt(),
                reportedUser != null ? reportedUser.getStatus() : null,
                reportedUser != null ? reportedUser.getSanctionType() : null
        );
    }
}
