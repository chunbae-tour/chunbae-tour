package com.chunbaetour.domain.report.service;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.report.entity.ReportTargetType;
import com.chunbaetour.domain.report.entity.SanctionType;
import com.chunbaetour.domain.report.entity.UserSanction;
import com.chunbaetour.domain.report.repository.UserSanctionRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 도메인별 제재 평가·적용 서비스.
 *
 * <p>누적 신고 임계치(3/5/10/15)는 도메인별로 독립 집계된다.
 * 2개 이상 도메인에 활성 제재가 동시에 존재하면 계정 전체 정지 트리거.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SanctionService {

    private final UserSanctionRepository userSanctionRepository;
    private final AccountRepository accountRepository;
    private final Clock clock;

    /**
     * 신고 승인 후 도메인별 제재 평가 및 적용.
     * ReportAcceptedEvent → SanctionListener → 본 메서드 (BEFORE_COMMIT, 동일 트랜잭션).
     */
    @Transactional
    public void handleReportAccepted(Long reportId, Long userId, ReportTargetType targetType,
                                     long acceptedCount) {
        LocalDateTime now = LocalDateTime.now(clock);

        // 1. 이벤트에서 전달받은 누적 RESOLVED 건수로 제재 단계 계산
        SanctionType calculated = SanctionType.fromCount(acceptedCount);
        if (calculated == SanctionType.NONE) return;

        // 2. 이미 같은 단계 이상 집행됐으면 스킵 (중복 제재 방지)
        SanctionType highest = userSanctionRepository
                .findByUserIdAndTargetType(userId, targetType)
                .stream()
                .map(UserSanction::getSanctionType)
                .max(Comparator.comparingInt(SanctionType::severity))
                .orElse(SanctionType.NONE);

        if (!calculated.isHigherThan(highest)) return;

        // 3. 제재 이력 저장
        LocalDateTime endedAt = calculateEndAt(calculated, now);
        UserSanction sanction = UserSanction.create(
                userId, reportId, targetType, calculated,
                "누적 신고 " + acceptedCount + "건 도달 — 자동 제재", now, endedAt);
        userSanctionRepository.save(sanction);

        log.info("[제재] userId={} targetType={} sanctionType={} endedAt={}", userId, targetType, calculated, endedAt);
        // TODO: SecurityAuditLogger 연동 — 자동 제재 적용 이력 감사 로그 기록

        // 4. USER·MERCHANT 도메인 → Account 직접 정지 (계정 전체 차단)
        if ((targetType == ReportTargetType.USER || targetType == ReportTargetType.MERCHANT)
                && calculated != SanctionType.WARNING) {
            applyAccountSuspension(userId, calculated, endedAt);
        }

        // 5. 2+ 활성 도메인 → 계정 전체 정지
        checkAndApplyCrossDomainSuspension(userId, now);
    }

    /** 제재 종료 시각 계산. WARNING은 started_at과 동일(즉시 만료 — 차단 없음). */
    public LocalDateTime calculateEndAt(SanctionType type, LocalDateTime from) {
        return switch (type) {
            case WARNING -> from;
            case SUSPEND_7D -> from.plusDays(7);
            case SUSPEND_30D -> from.plusDays(30);
            case PERMANENT, NONE -> null;
        };
    }

    // ── 내부 ─────────────────────────────────────────────────────────────────

    private void applyAccountSuspension(Long userId, SanctionType type, LocalDateTime endedAt) {
        accountRepository.findById(userId).ifPresentOrElse(
                acc -> acc.applySystemSanction(type, endedAt),
                () -> log.warn("[제재] Account 없음 userId={}", userId));
    }

    /**
     * 2개 이상 도메인에 활성 제재 존재 시 Account 전체 정지.
     * 최고 SanctionType + max(endedAt) 적용 (PERMANENT가 있으면 endedAt=null).
     */
    private void checkAndApplyCrossDomainSuspension(Long userId, LocalDateTime now) {
        long activeDomains = userSanctionRepository.countActiveDistinctDomainsByUserId(userId, now);
        if (activeDomains < 2) return;

        List<UserSanction> allActive = userSanctionRepository.findAllActiveSanctionsByUserId(userId, now);

        SanctionType maxType = allActive.stream()
                .map(UserSanction::getSanctionType)
                .max(Comparator.comparingInt(SanctionType::severity))
                .orElse(SanctionType.NONE);

        // PERMANENT가 포함된 경우 endedAt = null (무기한)
        boolean hasPermanent = allActive.stream()
                .anyMatch(s -> s.getSanctionType() == SanctionType.PERMANENT);
        LocalDateTime maxEndAt = hasPermanent ? null : allActive.stream()
                .map(UserSanction::getEndedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        log.info("[크로스도메인 정지] userId={} activeDomains={} maxType={} maxEndAt={}",
                userId, activeDomains, maxType, maxEndAt);
        applyAccountSuspension(userId, maxType, maxEndAt);
    }
}
