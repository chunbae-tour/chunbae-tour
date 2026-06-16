package com.chunbaetour.domain.report.validator;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.report.entity.ReportTargetType;
import com.chunbaetour.domain.shop.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MerchantReportTargetValidator implements ReportTargetValidator {

    private final ShopService shopService;

    @Override
    public ReportTargetType supportType() { return ReportTargetType.MERCHANT; }

    @Override
    public Long validate(Long targetId, Long reporterId) {
        Long ownerId = shopService.findMerchantAccountId(targetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_TARGET_NOT_FOUND));
        if (ownerId.equals(reporterId))
            throw new BusinessException(ErrorCode.REPORT_SELF);
        return ownerId;
    }
}
