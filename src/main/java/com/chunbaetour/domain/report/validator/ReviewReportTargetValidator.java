package com.chunbaetour.domain.report.validator;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.place.PlaceReviewStatus;
import com.chunbaetour.domain.place.repository.PlaceReviewRepository;
import com.chunbaetour.domain.report.entity.ReportTargetType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReviewReportTargetValidator implements ReportTargetValidator {

    private final PlaceReviewRepository placeReviewRepository;

    @Override
    public ReportTargetType supportType() { return ReportTargetType.REVIEW; }

    @Override
    public Long validate(Long targetId, Long reporterId) {
        var review = placeReviewRepository.findById(targetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_TARGET_NOT_FOUND));
        if (review.getStatus() == PlaceReviewStatus.DELETED)
            throw new BusinessException(ErrorCode.REPORT_TARGET_INACTIVE);
        if (review.getAuthor().getId().equals(reporterId))
            throw new BusinessException(ErrorCode.REPORT_SELF);
        return review.getAuthor().getId();
    }
}
