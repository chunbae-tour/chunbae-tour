package com.chunbaetour.domain.report.validator;

import com.chunbaetour.domain.community.comment.entity.CommentStatus;
import com.chunbaetour.domain.community.comment.repository.CommentRepository;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.report.entity.ReportTargetType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CommentReportTargetValidator implements ReportTargetValidator {

    private final CommentRepository commentRepository;

    @Override
    public ReportTargetType supportType() { return ReportTargetType.COMMENT; }

    @Override
    public Long validate(Long targetId, Long reporterId) {
        var comment = commentRepository.findById(targetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_TARGET_NOT_FOUND));
        if (comment.getStatus() == CommentStatus.DELETED)
            throw new BusinessException(ErrorCode.REPORT_TARGET_INACTIVE);
        if (comment.getAuthorId().equals(reporterId))
            throw new BusinessException(ErrorCode.REPORT_SELF);
        return comment.getAuthorId();
    }
}
