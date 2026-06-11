package com.chunbaetour.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.report.entity.ReportTargetType;
import com.chunbaetour.domain.report.entity.SanctionType;
import com.chunbaetour.domain.report.entity.UserSanction;
import com.chunbaetour.domain.report.repository.UserSanctionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SuspendExpirySchedulerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 11, 12, 0, 0);

    @Spy
    Clock clock = Clock.fixed(Instant.parse("2026-06-11T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    AccountRepository accountRepository;

    @Mock
    UserSanctionRepository userSanctionRepository;

    @InjectMocks
    SuspendExpiryScheduler scheduler;

    @Test
    void releaseExpiredSanctions_releases_expired_user_sanctions() {
        UserSanction expired = UserSanction.create(
                1L, 10L, ReportTargetType.USER, SanctionType.SUSPEND_7D,
                "7일 제재", NOW.minusDays(8), NOW.minusHours(1));
        given(userSanctionRepository.findExpiredUnreleasedSanctions(any())).willReturn(List.of(expired));
        given(accountRepository.findExpiredSystemSanctions(any())).willReturn(List.of());

        scheduler.releaseExpiredSanctions();

        assertThat(expired.getReleasedAt()).isNotNull();
        assertThat(expired.getReleasedAt()).isEqualTo(NOW);
    }

    @Test
    void releaseExpiredSanctions_clears_expired_account_system_sanctions() {
        Account account = Account.registerUser("exp@test.com", "hash", "만료계정");
        account.applySystemSanction(SanctionType.SUSPEND_7D, NOW.minusHours(1));
        given(userSanctionRepository.findExpiredUnreleasedSanctions(any())).willReturn(List.of());
        given(accountRepository.findExpiredSystemSanctions(any())).willReturn(List.of(account));

        scheduler.releaseExpiredSanctions();

        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getSanctionType()).isNull();
        assertThat(account.getSanctionEndAt()).isNull();
    }

    @Test
    void releaseExpiredSanctions_does_not_process_active_account_with_stale_sanction_fields() {
        // unsuspend() 후 sanctionEndAt 잔류 시 스케줄러가 재처리하는 버그 회귀 방지
        // findExpiredSystemSanctions 쿼리가 status=SUSPENDED 필터 적용하므로 ACTIVE 계정은 반환 안 됨
        given(userSanctionRepository.findExpiredUnreleasedSanctions(any())).willReturn(List.of());
        given(accountRepository.findExpiredSystemSanctions(any())).willReturn(List.of());

        scheduler.releaseExpiredSanctions();

        verify(accountRepository, never()).save(any());
    }

    @Test
    void releaseExpiredSanctions_no_expired_records_does_nothing() {
        given(userSanctionRepository.findExpiredUnreleasedSanctions(any())).willReturn(List.of());
        given(accountRepository.findExpiredSystemSanctions(any())).willReturn(List.of());

        scheduler.releaseExpiredSanctions();

        verify(userSanctionRepository, never()).save(any());
        verify(accountRepository, never()).save(any());
    }
}
