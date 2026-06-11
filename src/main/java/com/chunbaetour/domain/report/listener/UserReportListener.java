package com.chunbaetour.domain.report.listener;

import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.auth.AccountStatus;
import com.chunbaetour.domain.report.entity.ReportTargetType;
import com.chunbaetour.domain.report.event.ReportContentActionEvent;
import com.chunbaetour.domain.report.type.ReportAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserReportListener {

    private final AccountRepository accountRepository;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(ReportContentActionEvent event) {
        if (event.targetType() != ReportTargetType.USER) return;

        if (event.action() == ReportAction.DELETE || event.action() == ReportAction.SUSPEND) {
            accountRepository.findById(event.targetId()).ifPresentOrElse(acc -> {
                if (acc.getStatus() != AccountStatus.DELETED
                        && acc.getStatus() != AccountStatus.SUSPENDED) acc.suspend();
            }, () -> log.warn("UserReportListener: account not found, targetId={}", event.targetId()));
        }
    }
}
