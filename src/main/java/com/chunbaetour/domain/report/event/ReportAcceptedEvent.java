package com.chunbaetour.domain.report.event;

import com.chunbaetour.domain.report.entity.ReportTargetType;

/**
 * 신고 승인 이벤트 — 관리자가 신고를 RESOLVED 처리했을 때 발행.
 * SanctionListener가 구독해 도메인별 제재 누적 체크를 수행한다.
 *
 * @param reportId       처리된 신고 ID
 * @param reportedUserId 피신고 콘텐츠 작성자(또는 피신고 유저) ID
 * @param targetType     신고 도메인 (POST_COMPANION, POST_FREE, COMMENT, USER, MERCHANT)
 * @param acceptedCount  피신고자의 해당 도메인 누적 RESOLVED 신고 건수 (현재 건 포함)
 */
public record ReportAcceptedEvent(
        Long reportId,
        Long reportedUserId,
        ReportTargetType targetType,
        int acceptedCount
) {
    public ReportAcceptedEvent {
        if (reportId == null || reportedUserId == null || targetType == null) {
            throw new IllegalArgumentException("ReportAcceptedEvent 필수 필드는 null일 수 없습니다.");
        }
        if (acceptedCount < 0) {
            throw new IllegalArgumentException("acceptedCount는 음수일 수 없습니다.");
        }
    }
}
