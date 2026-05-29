package com.chunbaetour.domain.report.dto.request;

import com.chunbaetour.domain.report.type.ReportAction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 가게 신고 처리 요청 DTO (KAN-92).
 * POST /admin/reports/{id}/resolve/merchant — targetType이 MERCHANT인 경우.
 *
 * @param action    HIDE_SHOP / REVOKE_MERCHANT / DISMISS 중 하나
 * @param adminNote 처리 메모 (선택, 최대 500자)
 */
public record MerchantReportResolveRequest(
        @NotNull(message = "action은 필수입니다.")
        ReportAction action,
        @Size(max = 500, message = "처리 메모는 500자 이하로 입력해 주세요.")
        String adminNote
) {}
