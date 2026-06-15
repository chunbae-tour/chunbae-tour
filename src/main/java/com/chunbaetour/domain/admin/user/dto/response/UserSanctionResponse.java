package com.chunbaetour.domain.admin.user.dto.response;

import com.chunbaetour.domain.auth.SanctionType;
import com.chunbaetour.domain.report.entity.ReportTargetType;
import com.chunbaetour.domain.report.entity.UserSanction;
import java.time.LocalDateTime;

public record UserSanctionResponse(
        Long sanctionId,
        Long reportId,
        ReportTargetType targetType,
        SanctionType sanctionType,
        String reason,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        LocalDateTime releasedAt,
        Long releasedBy,
        LocalDateTime createdAt
) {
    public static UserSanctionResponse of(UserSanction s) {
        return new UserSanctionResponse(
                s.getId(),
                s.getReportId(),
                s.getTargetType(),
                s.getSanctionType(),
                s.getReason(),
                s.getStartedAt(),
                s.getEndedAt(),
                s.getReleasedAt(),
                s.getReleasedBy(),
                s.getCreatedAt()
        );
    }
}
