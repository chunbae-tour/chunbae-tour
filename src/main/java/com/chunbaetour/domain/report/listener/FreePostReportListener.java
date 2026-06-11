package com.chunbaetour.domain.report.listener;

import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.auth.AccountStatus;
import com.chunbaetour.domain.community.free.entity.FreePost;
import com.chunbaetour.domain.community.free.entity.FreePostStatus;
import com.chunbaetour.domain.community.free.repository.FreePostRepository;
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
public class FreePostReportListener {

    private final FreePostRepository freePostRepository;
    private final AccountRepository accountRepository;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(ReportContentActionEvent event) {
        if (event.targetType() != ReportTargetType.POST_FREE) return;

        freePostRepository.findById(event.targetId()).ifPresentOrElse(post -> {
            if (event.action() == ReportAction.DELETE) {
                if (post.getStatus() == FreePostStatus.DELETED) {
                    log.warn("FreePostReportListener: already DELETED, postId={}", event.targetId());
                    return;
                }
                post.hide();
            } else if (event.action() == ReportAction.SUSPEND) {
                accountRepository.findById(post.getAuthorId()).ifPresent(acc -> {
                    if (acc.getStatus() != AccountStatus.DELETED) acc.suspend();
                });
            } else if (event.action() == ReportAction.RESTORE) {
                post.restore();
            }
        }, () -> log.warn("FreePostReportListener: post not found, targetId={}", event.targetId()));
    }
}
