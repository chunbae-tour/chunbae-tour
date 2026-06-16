package com.chunbaetour.domain.report.listener;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.auth.AccountStatus;
import com.chunbaetour.domain.community.companion.entity.CompanionPost;
import com.chunbaetour.domain.community.companion.entity.CompanionPostStatus;
import com.chunbaetour.domain.community.companion.repository.CompanionPostRepository;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.report.entity.ReportTargetType;
import com.chunbaetour.domain.report.entity.SanctionType;
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
public class CompanionPostReportListener {

    private final CompanionPostRepository companionPostRepository;
    private final AccountRepository accountRepository;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(ReportContentActionEvent event) {
        if (event.targetType() != ReportTargetType.POST_COMPANION) return;

        companionPostRepository.findById(event.targetId()).ifPresentOrElse(post -> {
            if (event.action() == ReportAction.DELETE) {
                if (post.getStatus() == CompanionPostStatus.DELETED) {
                    log.warn("CompanionPostReportListener: already DELETED, postId={}", event.targetId());
                    return;
                }
                post.hide();
            } else if (event.action() == ReportAction.SUSPEND) {
                accountRepository.findById(post.getAuthorId()).ifPresent(acc -> {
                    if (acc.getStatus() == AccountStatus.DELETED) return;
                    if (acc.getStatus() == AccountStatus.SUSPENDED) {
                        throw new BusinessException(ErrorCode.REPORT_TARGET_ALREADY_SUSPENDED);
                    }
                    acc.applySystemSanction(SanctionType.PERMANENT, null);
                });
            } else if (event.action() == ReportAction.RESTORE) {
                post.restore();
            }
        }, () -> log.warn("CompanionPostReportListener: post not found, targetId={}", event.targetId()));
    }
}
