package com.chunbaetour.domain.report.validator;

import com.chunbaetour.domain.community.free.entity.FreePostStatus;
import com.chunbaetour.domain.community.free.repository.FreePostRepository;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.report.entity.ReportTargetType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FreePostReportTargetValidator implements ReportTargetValidator {

    private final FreePostRepository freePostRepository;

    @Override
    public ReportTargetType supportType() { return ReportTargetType.POST_FREE; }

    @Override
    public Long validate(Long targetId, Long reporterId) {
        var post = freePostRepository.findById(targetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_TARGET_NOT_FOUND));
        if (post.getStatus() != FreePostStatus.ACTIVE)
            throw new BusinessException(ErrorCode.REPORT_TARGET_INACTIVE);
        if (post.getAuthorId().equals(reporterId))
            throw new BusinessException(ErrorCode.REPORT_SELF);
        return post.getAuthorId();
    }
}
