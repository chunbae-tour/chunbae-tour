package com.chunbaetour.domain.report.listener;

import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.auth.AccountStatus;
import com.chunbaetour.domain.community.comment.entity.CommentStatus;
import com.chunbaetour.domain.community.comment.repository.CommentRepository;
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
public class CommentReportListener {

    private final CommentRepository commentRepository;
    private final AccountRepository accountRepository;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(ReportContentActionEvent event) {
        if (event.targetType() != ReportTargetType.COMMENT) return;

        commentRepository.findById(event.targetId()).ifPresentOrElse(comment -> {
            if (event.action() == ReportAction.DELETE) {
                if (comment.getStatus() != CommentStatus.DELETED) comment.delete();
            } else if (event.action() == ReportAction.SUSPEND) {
                accountRepository.findById(comment.getAuthorId()).ifPresent(acc -> {
                    if (acc.getStatus() != AccountStatus.DELETED
                            && acc.getStatus() != AccountStatus.SUSPENDED) acc.suspend();
                });
            } else if (event.action() == ReportAction.RESTORE) {
                comment.restore();
            }
        }, () -> log.warn("CommentReportListener: comment not found, targetId={}", event.targetId()));
    }
}
