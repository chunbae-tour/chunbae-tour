package com.chunbaetour.domain.report.dto.request;

import com.chunbaetour.domain.report.type.ReportAction;
import jakarta.validation.constraints.NotNull;

/**
 * 가게 신고 처리 요청 DTO (KAN-92).
 * POST /admin/reports/{id}/resolve/merchant — targetType이 MERCHANT인 경우.
 *
 * @param action    HIDE_SHOP / REVOKE_MERCHANT / DISMISS 중 하나
 * @param adminNote 처리 메모 (선택)
 */
public record MerchantReportResolveRequest(
        @NotNull(message = "action은 필수입니다.")
        ReportAction action,
        String adminNote
) {}
