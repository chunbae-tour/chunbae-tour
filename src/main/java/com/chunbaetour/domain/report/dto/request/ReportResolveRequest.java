package com.chunbaetour.domain.report.dto.request;

import com.chunbaetour.domain.report.type.ReportAction;
import jakarta.validation.constraints.NotNull;

/**
 * 콘텐츠 신고 처리 요청 DTO (KAN-92).
 * POST /admin/reports/{id}/resolve — targetType이 POST·COMMENT·REVIEW·USER인 경우.
 *
 * @param action    WARNING / SUSPEND / DELETE / DISMISS 중 하나
 * @param adminNote 처리 메모 (선택)
 */
public record ReportResolveRequest(
        @NotNull(message = "action은 필수입니다.")
        ReportAction action,
        String adminNote
) {}
