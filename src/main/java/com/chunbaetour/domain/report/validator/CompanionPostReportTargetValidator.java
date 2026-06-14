package com.chunbaetour.domain.report.validator;

import com.chunbaetour.domain.community.companion.entity.CompanionPostStatus;
import com.chunbaetour.domain.community.companion.repository.CompanionPostRepository;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.report.entity.ReportTargetType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CompanionPostReportTargetValidator implements ReportTargetValidator {

    private final CompanionPostRepository companionPostRepository;

    @Override
    public ReportTargetType supportType() { return ReportTargetType.POST_COMPANION; }

    @Override
    public Long validate(Long targetId, Long reporterId) {
        var post = companionPostRepository.findById(targetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_TARGET_NOT_FOUND));
        if (post.getStatus() != CompanionPostStatus.ACTIVE)
            throw new BusinessException(ErrorCode.REPORT_TARGET_INACTIVE);
        if (post.getAuthorId().equals(reporterId))
            throw new BusinessException(ErrorCode.REPORT_SELF);
        return post.getAuthorId();
    }
}
