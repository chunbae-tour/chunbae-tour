package com.chunbaetour.domain.report.event;

import com.chunbaetour.domain.report.entity.ReportTargetType;
import com.chunbaetour.domain.report.type.ReportAction;

/**
 * 관리자 신고 처리 후 도메인별 콘텐츠 삭제·정지 리스너에 전달되는 이벤트.
 * DELETE/SUSPEND 액션에만 발행 (WARNING·DISMISS는 콘텐츠 변경 없음).
 */
public record ReportContentActionEvent(
        Long reportId,
        ReportTargetType targetType,
        Long targetId,
        ReportAction action
) {}
