package com.chunbaetour.domain.auth;

import com.chunbaetour.domain.report.repository.UserSanctionRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    @Transactional
    public void releaseExpiredSanctions() {
        LocalDateTime now = LocalDateTime.now(clock);

        // 1. user_sanction 만료 이력 일괄 release
        List<com.chunbaetour.domain.report.entity.UserSanction> expired =
                userSanctionRepository.findExpiredUnreleasedSanctions(now);
        expired.forEach(s -> s.release(null, now));
        if (!expired.isEmpty()) {
            log.info("[제재 만료] user_sanction {}건 release 처리 (기준: {})", expired.size(), now);
        }

        // 2. Account 정지 만료 일괄 해제
        List<Account> expiredAccounts = accountRepository.findExpiredSystemSanctions(now);
        expiredAccounts.forEach(Account::clearSystemSanction);
        if (!expiredAccounts.isEmpty()) {
            log.info("[제재 만료] Account {}건 ACTIVE 복구 (기준: {})", expiredAccounts.size(), now);
        }
    }
}
