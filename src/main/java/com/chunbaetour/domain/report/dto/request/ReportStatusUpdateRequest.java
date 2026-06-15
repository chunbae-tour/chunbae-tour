package com.chunbaetour.domain.report.dto.request;

import com.chunbaetour.domain.report.entity.ReportStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 신고 상태 정정 요청 DTO — 관리자 오판 정정.
 * PATCH /admin/reports/{id}/status — RESOLVED → DISMISSED 만 허용.
 *
 * @param status    변경할 상태 (현재 DISMISSED만 유효)
 * @param adminNote 정정 사유 (선택, 최대 500자)
 */
public record ReportStatusUpdateRequest(
        @NotNull(message = "status는 필수입니다.")
        ReportStatus status,
        @Size(max = 500, message = "정정 사유는 500자 이하로 입력해 주세요.")
        String adminNote
) {

    /** 정정 대상 상태는 DISMISSED만 허용 — 그 외 값은 DB 왕복 없이 즉시 400. (status null은 @NotNull이 처리) */
    @AssertTrue(message = "상태 정정은 DISMISSED로만 가능합니다.")
    public boolean isDismissedTarget() {
        return status == null || status == ReportStatus.DISMISSED;
    }
}
