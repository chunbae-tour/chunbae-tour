package com.chunbaetour.domain.admin.user.service;

import com.chunbaetour.domain.admin.user.dto.response.UserSanctionResponse;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.report.entity.ReportTargetType;
import com.chunbaetour.domain.report.entity.UserSanction;
import com.chunbaetour.domain.report.repository.UserSanctionRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminSanctionService {

    private final UserSanctionRepository userSanctionRepository;
    private final AccountRepository accountRepository;
    private final Clock clock;

    /** 유저 제재 이력 전체 조회 (최신순). */
    public List<UserSanctionResponse> getSanctions(Long userId) {
        if (!accountRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return userSanctionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(UserSanctionResponse::of)
                .toList();
    }

    /**
     * 특정 제재 조기 해제.
     *
     * <p>해제 후 잔여 활성 제재가 없으면 Account의 시스템 제재도 함께 해제.
     * PERMANENT 포함 모든 단계 해제 가능 (관리자 강제 해제 {@code adminClearSystemSanction} 사용).
     */
    @Transactional
    public void releaseSanction(Long sanctionId, Long adminId) {
        UserSanction sanction = userSanctionRepository.findById(sanctionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SANCTION_NOT_FOUND));

        sanction.release(adminId, LocalDateTime.now(clock)); // 이미 해제된 경우 무시 (idempotent)

        Long userId = sanction.getUserId();
        LocalDateTime now = LocalDateTime.now(clock);

        List<UserSanction> remaining = userSanctionRepository.findAllActiveSanctionsByUserId(userId, now);

        // 잔여 활성 제재 없으면 Account 시스템 제재 해제
        if (remaining.isEmpty()) {
            accountRepository.findById(userId).ifPresent(acc -> {
                if (acc.getSanctionType() != null) {
                    acc.adminClearSystemSanction();
                }
            });
            return;
        }

        // 잔여 제재가 있어도 USER/MERCHANT 도메인이 없고 활성 도메인 수 < 2이면 계정 정지 해제
        long activeDomains = remaining.stream()
                .map(UserSanction::getTargetType)
                .distinct()
                .count();
        boolean hasAccountLevelDomain = remaining.stream()
                .map(UserSanction::getTargetType)
                .anyMatch(t -> t == ReportTargetType.USER || t == ReportTargetType.MERCHANT);

        if (!hasAccountLevelDomain && activeDomains < 2) {
            accountRepository.findById(userId).ifPresent(acc -> {
                if (acc.getSanctionType() != null) {
                    acc.adminClearSystemSanction();
                }
            });
        }
    }
}
