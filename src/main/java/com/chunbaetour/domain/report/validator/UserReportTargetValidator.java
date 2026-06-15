package com.chunbaetour.domain.report.validator;

import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.auth.AccountStatus;
import com.chunbaetour.domain.auth.Role;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.report.entity.ReportTargetType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserReportTargetValidator implements ReportTargetValidator {

    private final AccountRepository accountRepository;

    @Override
    public ReportTargetType supportType() { return ReportTargetType.USER; }

    @Override
    public Long validate(Long targetId, Long reporterId) {
        var account = accountRepository.findById(targetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_TARGET_NOT_FOUND));
        if (account.getRole() != Role.USER)
            throw new BusinessException(ErrorCode.REPORT_TARGET_NOT_FOUND);
        if (account.getStatus() == AccountStatus.DELETED)
            throw new BusinessException(ErrorCode.REPORT_TARGET_INACTIVE);
        // 자기신고는 create()에서 DB 조회 전 먼저 차단됨
        return targetId;
    }
}
