package com.chunbaetour.domain.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.auth.AccountStatus;
import com.chunbaetour.domain.report.entity.ReportTargetType;
import com.chunbaetour.domain.report.entity.SanctionType;
import com.chunbaetour.domain.report.entity.UserSanction;
import com.chunbaetour.domain.report.repository.UserSanctionRepository;
import com.chunbaetour.domain.report.service.SanctionService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SanctionServiceTest {

    private static final long USER_ID = 10L;
    private static final long REPORT_ID = 99L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 11, 12, 0, 0);

    @Spy
    Clock clock = Clock.fixed(Instant.parse("2026-06-11T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    UserSanctionRepository userSanctionRepository;

    @Mock
    AccountRepository accountRepository;

    @InjectMocks
    SanctionService sanctionService;

    @Test
    void handleReportAccepted_count3_USER_saves_WARNING_no_account_suspend() {
        given(userSanctionRepository.findByUserIdAndTargetType(USER_ID, ReportTargetType.USER))
                .willReturn(List.of());
        given(userSanctionRepository.countActiveDistinctDomainsByUserId(eq(USER_ID), any()))
                .willReturn(0L);

        sanctionService.handleReportAccepted(REPORT_ID, USER_ID, ReportTargetType.USER, 3);

        verify(userSanctionRepository).save(any(UserSanction.class));
        // WARNING은 계정 정지 유발하지 않음
        verify(accountRepository, never()).findById(any());
    }

    @Test
    void handleReportAccepted_count5_USER_saves_SUSPEND7D_and_suspends_account() {
        Account account = Account.registerUser("u@test.com", "hash", "유저");
        given(userSanctionRepository.findByUserIdAndTargetType(USER_ID, ReportTargetType.USER))
                .willReturn(List.of());
        given(accountRepository.findById(USER_ID)).willReturn(Optional.of(account));
        given(userSanctionRepository.countActiveDistinctDomainsByUserId(eq(USER_ID), any()))
                .willReturn(0L);

        sanctionService.handleReportAccepted(REPORT_ID, USER_ID, ReportTargetType.USER, 5);

        verify(userSanctionRepository).save(any(UserSanction.class));
        assertThat(account.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(account.getSanctionType()).isEqualTo(SanctionType.SUSPEND_7D);
        assertThat(account.getSanctionEndAt()).isEqualTo(NOW.plusDays(7));
    }

    @Test
    void handleReportAccepted_same_level_already_exists_skips() {
        UserSanction existing = UserSanction.create(
                USER_ID, 10L, ReportTargetType.USER, SanctionType.SUSPEND_7D,
                "기존 제재", NOW.minusDays(1), NOW.plusDays(6));
        given(userSanctionRepository.findByUserIdAndTargetType(USER_ID, ReportTargetType.USER))
                .willReturn(List.of(existing));

        sanctionService.handleReportAccepted(REPORT_ID, USER_ID, ReportTargetType.USER, 5);

        verify(userSanctionRepository, never()).save(any(UserSanction.class));
        verify(accountRepository, never()).findById(any());
    }

    @Test
    void handleReportAccepted_count0_returns_without_save() {
        sanctionService.handleReportAccepted(REPORT_ID, USER_ID, ReportTargetType.POST_COMPANION, 0);

        verify(userSanctionRepository, never()).save(any(UserSanction.class));
        verify(accountRepository, never()).findById(any());
    }

    @Test
    void handleReportAccepted_cross_domain_2_active_triggers_account_suspension() {
        Account account = Account.registerUser("cross@test.com", "hash", "크로스유저");
        given(userSanctionRepository.findByUserIdAndTargetType(USER_ID, ReportTargetType.POST_COMPANION))
                .willReturn(List.of());
        // 2개 도메인 활성 → 계정 정지 트리거
        given(userSanctionRepository.countActiveDistinctDomainsByUserId(eq(USER_ID), any()))
                .willReturn(2L);
        UserSanction s1 = UserSanction.create(USER_ID, 1L, ReportTargetType.COMMENT,
                SanctionType.SUSPEND_7D, "도메인1", NOW.minusDays(1), NOW.plusDays(6));
        UserSanction s2 = UserSanction.create(USER_ID, 2L, ReportTargetType.POST_COMPANION,
                SanctionType.SUSPEND_7D, "도메인2", NOW, NOW.plusDays(7));
        given(userSanctionRepository.findAllActiveSanctionsByUserId(eq(USER_ID), any()))
                .willReturn(List.of(s1, s2));
        given(accountRepository.findById(USER_ID)).willReturn(Optional.of(account));

        sanctionService.handleReportAccepted(REPORT_ID, USER_ID, ReportTargetType.POST_COMPANION, 5);

        assertThat(account.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(account.getSanctionType()).isEqualTo(SanctionType.SUSPEND_7D);
    }

    @Test
    void handleReportAccepted_cross_domain_does_not_downgrade_existing_permanent() {
        // Account에 이미 PERMANENT 적용된 상태에서 크로스도메인이 SUSPEND_7D를 applySystemSanction으로 시도 →
        // applySystemSanction severity 가드로 PERMANENT 보호됨을 검증
        Account account = Account.registerUser("perm-nodeg@test.com", "hash", "영구정지");
        account.applySystemSanction(SanctionType.PERMANENT, null);

        given(userSanctionRepository.findByUserIdAndTargetType(USER_ID, ReportTargetType.POST_FREE))
                .willReturn(List.of());
        given(userSanctionRepository.countActiveDistinctDomainsByUserId(eq(USER_ID), any()))
                .willReturn(2L);
        UserSanction s1 = UserSanction.create(USER_ID, 1L, ReportTargetType.COMMENT,
                SanctionType.SUSPEND_7D, "도메인1", NOW.minusDays(1), NOW.plusDays(6));
        UserSanction s2 = UserSanction.create(USER_ID, 2L, ReportTargetType.POST_FREE,
                SanctionType.SUSPEND_7D, "도메인2", NOW, NOW.plusDays(7));
        given(userSanctionRepository.findAllActiveSanctionsByUserId(eq(USER_ID), any()))
                .willReturn(List.of(s1, s2));
        given(accountRepository.findById(USER_ID)).willReturn(Optional.of(account));

        sanctionService.handleReportAccepted(REPORT_ID, USER_ID, ReportTargetType.POST_FREE, 5);

        // PERMANENT는 유지돼야 함
        assertThat(account.getSanctionType()).isEqualTo(SanctionType.PERMANENT);
        assertThat(account.getSanctionEndAt()).isNull();
    }

    @Test
    void calculateEndAt_WARNING_returns_same_instant() {
        assertThat(sanctionService.calculateEndAt(SanctionType.WARNING, NOW)).isEqualTo(NOW);
    }

    @Test
    void calculateEndAt_SUSPEND_7D_returns_plus7days() {
        assertThat(sanctionService.calculateEndAt(SanctionType.SUSPEND_7D, NOW))
                .isEqualTo(NOW.plusDays(7));
    }

    @Test
    void calculateEndAt_PERMANENT_returns_null() {
        assertThat(sanctionService.calculateEndAt(SanctionType.PERMANENT, NOW)).isNull();
    }
}
