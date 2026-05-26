package com.chunbaetour.domain.report.dto;

import com.chunbaetour.domain.report.entity.ReportReason;
import com.chunbaetour.domain.report.entity.ReportTargetType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ReportCreateRequest(
        @NotNull ReportTargetType targetType,
        @NotNull @Positive Long targetId,
        @NotNull ReportReason reason,
        @Size(max = 500) String description
) {
}
