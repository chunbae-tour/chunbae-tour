package com.chunbaetour.domain.report.event;

import com.chunbaetour.domain.report.service.SanctionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 신고 승인 이벤트 구독 — 도메인별 누적 제재 평가 트리거.
 * BEFORE_COMMIT: 신고 처리 트랜잭션과 동일한 트랜잭션에서 실행되어 원자성 보장.
 */
@Component
@RequiredArgsConstructor
public class SanctionListener {

    private final SanctionService sanctionService;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleReportAccepted(ReportAcceptedEvent event) {
        sanctionService.handleReportAccepted(
                event.reportId(),
                event.reportedUserId(),
                event.targetType());
    }
}
