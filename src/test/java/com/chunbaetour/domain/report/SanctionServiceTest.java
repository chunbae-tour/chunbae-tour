package com.chunbaetour.domain.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.auth.AccountStatus;
import com.chunbaetour.domain.auth.Role;
import com.chunbaetour.domain.report.entity.ReportTargetType;
import com.chunbaetour.domain.report.entity.SanctionType;
import com.chunbaetour.domain.report.repository.UserSanctionRepository;
import com.chunbaetour.domain.report.service.SanctionService;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SanctionService 단위 테스트")
class SanctionServiceTest {

    private static final Long USER_ID   = 10L;
    private static final Long REPORT_ID = 1L;

    @Mock private UserSanctionRepository userSanctionRepository;
    @Mock private AccountRepository accountRepository;
    @Spy  Clock clock = Clock.fixed(Instant.parse("2026-06-11T00:00:00Z"), ZoneOffset.UTC);

    @InjectMocks
    private SanctionService sanctionService;

    @Test
    @DisplayName("acceptedCount=3 → WARNING — Account 상태 변경 없음, 이력만 저장")
    void handleReportAccepted_3건_WARNING_계정정지_없음() {
        given(userSanctionRepository.findByUserIdAndTargetType(USER_ID, ReportTargetType.USER))
                .willReturn(List.of());
        given(userSanctionRepository.countActiveDistinctDomainsByUserId(any(), any())).willReturn(0L);

        sanctionService.handleReportAccepted(REPORT_ID, USER_ID, ReportTargetType.USER, 3);

        then(userSanctionRepository).should().save(
                argThat(s -> s.getSanctionType() == SanctionType.WARNING));
        then(accountRepository).should(never()).findById(any());
    }

    @Test
    @DisplayName("acceptedCount=5 → SUSPEND_7D — UserSanction 저장 + Account 정지")
    void handleReportAccepted_5건_SUSPEND_7D_계정정지() {
        Account account = activeAccount(USER_ID);
        given(userSanctionRepository.findByUserIdAndTargetType(USER_ID, ReportTargetType.USER))
                .willReturn(List.of());
        given(accountRepository.findById(USER_ID)).willReturn(Optional.of(account));
        given(userSanctionRepository.countActiveDistinctDomainsByUserId(any(), any())).willReturn(1L);

        sanctionService.handleReportAccepted(REPORT_ID, USER_ID, ReportTargetType.USER, 5);

        then(userSanctionRepository).should().save(
                argThat(s -> s.getSanctionType() == SanctionType.SUSPEND_7D));
        assertThat(account.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(account.getSanctionType()).isEqualTo(SanctionType.SUSPEND_7D);
    }

    @Test
    @DisplayName("이미 동일 단계 제재 존재 → 중복 제재 스킵")
    void handleReportAccepted_동일단계_이미존재_스킵() {
        com.chunbaetour.domain.report.entity.UserSanction existing =
                com.chunbaetour.domain.report.entity.UserSanction.create(
                        USER_ID, 99L, ReportTargetType.USER, SanctionType.SUSPEND_7D,
                        "기존 제재", java.time.LocalDateTime.now(clock),
                        java.time.LocalDateTime.now(clock).plusDays(7));
        given(userSanctionRepository.findByUserIdAndTargetType(USER_ID, ReportTargetType.USER))
                .willReturn(List.of(existing));

        sanctionService.handleReportAccepted(REPORT_ID, USER_ID, ReportTargetType.USER, 5);

        then(userSanctionRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("acceptedCount=2 → NONE — 제재 없음")
    void handleReportAccepted_2건_NONE_제재없음() {
        sanctionService.handleReportAccepted(REPORT_ID, USER_ID, ReportTargetType.USER, 2);

        then(userSanctionRepository).should(never()).save(any());
        then(accountRepository).should(never()).findById(any());
    }

    private static Account activeAccount(long id) {
        Account account = Account.registerUser("test@test.com", "pw", "tester");
        writeField(account, "id", id);
        writeField(account, "role", Role.USER);
        writeField(account, "status", AccountStatus.ACTIVE);
        return account;
    }

    private static void writeField(Account account, String name, Object value) {
        try {
            Field field = Account.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(account, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
