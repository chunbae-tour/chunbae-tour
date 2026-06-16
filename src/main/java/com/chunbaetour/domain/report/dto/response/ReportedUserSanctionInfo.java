package com.chunbaetour.domain.report.dto.response;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountStatus;
import com.chunbaetour.domain.report.entity.ReportTargetType;
import com.chunbaetour.domain.report.entity.SanctionType;
import com.chunbaetour.domain.report.entity.UserSanction;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 피신고 유저의 현재 제재 상태 — 신고 상세 조회용.
 *
 * <p>2층 구조: 계정 레벨(전체 정지) + 도메인 레벨(활성 user_sanction 목록).
 * 관리자가 신고를 검토할 때 대상 유저가 현재 어떤 제재를 받고 있는지 한눈에 파악.
 */
public record ReportedUserSanctionInfo(
        AccountStatus accountStatus,
        SanctionType accountSanctionType,
        LocalDateTime accountSanctionEndAt,
        List<DomainSanction> activeDomainSanctions
) {
    public record DomainSanction(
            ReportTargetType targetType,
            SanctionType sanctionType,
            LocalDateTime endedAt
    ) {
        public static DomainSanction of(UserSanction s) {
            return new DomainSanction(s.getTargetType(), s.getSanctionType(), s.getEndedAt());
        }
    }

    public static ReportedUserSanctionInfo of(Account account, List<UserSanction> activeSanctions) {
        return new ReportedUserSanctionInfo(
                account.getStatus(),
                account.getSanctionType(),
                account.getSanctionEndAt(),
                activeSanctions.stream().map(DomainSanction::of).toList()
        );
    }
}
