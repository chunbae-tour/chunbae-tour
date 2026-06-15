package com.chunbaetour.domain.report.listener;

import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.auth.AccountStatus;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.place.PlaceReviewStatus;
import com.chunbaetour.domain.place.repository.PlaceReviewRepository;
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
public class ReviewReportListener {

    private final PlaceReviewRepository placeReviewRepository;
    private final AccountRepository accountRepository;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(ReportContentActionEvent event) {
        if (event.targetType() != ReportTargetType.REVIEW) return;

        placeReviewRepository.findById(event.targetId()).ifPresentOrElse(review -> {
            if (event.action() == ReportAction.DELETE) {
                if (review.getStatus() == PlaceReviewStatus.DELETED) {
                    log.warn("ReviewReportListener: already DELETED, reviewId={}", event.targetId());
                    return;
                }
                review.delete();
            } else if (event.action() == ReportAction.SUSPEND) {
                accountRepository.findById(review.getAuthor().getId()).ifPresent(acc -> {
                    if (acc.getStatus() == AccountStatus.DELETED) return;
                    if (acc.getStatus() == AccountStatus.SUSPENDED) {
                        throw new BusinessException(ErrorCode.REPORT_TARGET_ALREADY_SUSPENDED);
                    }
                    acc.applySystemSanction(SanctionType.PERMANENT, null);
                });
            } else if (event.action() == ReportAction.RESTORE) {
                review.restore();
            }
        }, () -> log.warn("ReviewReportListener: review not found, targetId={}", event.targetId()));
    }
}
