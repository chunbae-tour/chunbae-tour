package com.chunbaetour.domain.auth;

import com.chunbaetour.domain.report.repository.UserSanctionRepository;
import com.chunbaetour.domain.report.entity.UserSanction;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기간 정지 만료 스케줄러 — 매시간 실행.
 *
 * <p>두 가지를 처리한다:
 * <ol>
 *   <li>{@code user_sanction} 만료 이력 release 처리 (released_at 기록)</li>
 *   <li>{@code users.sanction_end_at < now} 인 계정 SUSPENDED → ACTIVE 해제</li>
 * </ol>
 *
 * <p>PERMANENT 제재는 {@code ended_at IS NULL} 이므로 자동 제외.
 * 로그인 시점 만료 체크(LoginService·ReissueService·OauthLoginService)와 함께 동작해
 * DB 정합성을 보장한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SuspendExpiryScheduler {

    private final AccountRepository accountRepository;
    private final UserSanctionRepository userSanctionRepository;
    private final Clock clock;

    @Scheduled(cron = "0 0 * * * *")
    @SchedulerLock(name = "suspend_expiry", lockAtMostFor = "PT3M", lockAtLeastFor = "PT30S")
    @Transactional
    public void releaseExpiredSanctions() {
        LocalDateTime now = LocalDateTime.now(clock);

        // 1. user_sanction 만료 이력 일괄 release
        // TODO: SecurityAuditLogger 연동 — 자동 제재 해제 이력 감사 로그 기록
        List<UserSanction> expired = userSanctionRepository.findExpiredUnreleasedSanctions(now);
        int releasedCount = 0;
        for (UserSanction s : expired) {
            try {
                s.release(null, now);
                releasedCount++;
            } catch (Exception e) {
                log.warn("[제재 만료] UserSanction {} release 실패: {}", s.getId(), e.getMessage());
            }
        }
        if (!expired.isEmpty()) {
            log.info("[제재 만료] user_sanction {}건 중 {}건 release 처리 (기준: {})",
                    expired.size(), releasedCount, now);
        }

        // 2. Account 정지 만료 일괄 해제
        List<Account> expiredAccounts = accountRepository.findExpiredSystemSanctions(
                AccountStatus.SUSPENDED, now);
        int clearedCount = 0;
        for (Account acc : expiredAccounts) {
            try {
                acc.clearSystemSanction();
                clearedCount++;
            } catch (Exception e) {
                log.warn("[제재 만료] Account {} clearSystemSanction 실패: {}", acc.getId(), e.getMessage());
            }
        }
        if (!expiredAccounts.isEmpty()) {
            log.info("[제재 만료] Account {}건 중 {}건 ACTIVE 복구 (기준: {})",
                    expiredAccounts.size(), clearedCount, now);
        }
    }
}
