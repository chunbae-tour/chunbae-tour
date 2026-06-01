package com.chunbaetour.domain.admin.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.auth.AccountStatus;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.report.entity.ReportTargetType;
import com.chunbaetour.domain.report.repository.ReportRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link AdminUserService} 단위 테스트 (KAN-180 Admin S02).
 *
 * <p>repository/clock을 mock해 정지/해제 위임, durationDays→suspendedUntil 산출, 미존재 404,
 * S03 대시보드 의존 카운트 3종을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-05-31T03:00:00Z");
    private final Clock fixedClock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private ReportRepository reportRepository;

    private AdminUserService service() {
        return new AdminUserService(accountRepository, reportRepository, fixedClock);
    }

    private Account activeUser() {
        return Account.registerUser("u@example.com", "hash", "유저");
    }

    @Test
    @DisplayName("정지: durationDays 지정 → 현재 시각 + 일수로 suspendedUntil 산출")
    void suspend_withDuration_setsSuspendedUntil() {
        Account account = activeUser();
        given(accountRepository.findById(1L)).willReturn(Optional.of(account));

        service().suspend(1L, "신고 누적", 7);

        LocalDateTime expectedUntil = LocalDateTime.now(fixedClock).plusDays(7);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(account.getSuspendedReason()).isEqualTo("신고 누적");
        assertThat(account.getSuspendedUntil()).isEqualTo(expectedUntil);
    }

    @Test
    @DisplayName("정지: durationDays null → 무기한(suspendedUntil null)")
    void suspend_withoutDuration_isIndefinite() {
        Account account = activeUser();
        given(accountRepository.findById(1L)).willReturn(Optional.of(account));

        service().suspend(1L, "무기한 사유", null);

        assertThat(account.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(account.getSuspendedUntil()).isNull();
    }

    @Test
    @DisplayName("정지: 미존재 사용자 → USER_NOT_FOUND")
    void suspend_userNotFound_throws() {
        given(accountRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service().suspend(999L, "사유", 3))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("해제: status ACTIVE + 필드 초기화")
    void unsuspend_resetsAccount() {
        Account account = activeUser();
        account.suspend("사유", LocalDateTime.now(fixedClock));
        given(accountRepository.findById(1L)).willReturn(Optional.of(account));

        service().unsuspend(1L);

        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getSuspendedReason()).isNull();
        assertThat(account.getSuspendedUntil()).isNull();
    }

    @Test
    @DisplayName("해제: 미존재 사용자 → USER_NOT_FOUND")
    void unsuspend_userNotFound_throws() {
        given(accountRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service().unsuspend(999L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("상세: 누적 신고 건수를 report repository에서 조합")
    void getUser_includesReportCount() {
        Account account = activeUser();
        given(accountRepository.findById(1L)).willReturn(Optional.of(account));
        given(reportRepository.countByTargetTypeAndTargetId(ReportTargetType.USER, 1L)).willReturn(5L);

        var detail = service().getUser(1L);

        assertThat(detail.reportCount()).isEqualTo(5L);
    }

    @Test
    @DisplayName("카운트 3종: repository에 위임")
    void countMethods_delegateToRepository() {
        given(accountRepository.count()).willReturn(42L);
        given(accountRepository.countByStatus(AccountStatus.SUSPENDED)).willReturn(3L);
        given(accountRepository.countByCreatedAtGreaterThanEqual(any())).willReturn(7L);

        AdminUserService service = service();
        assertThat(service.getTotalUsers()).isEqualTo(42L);
        assertThat(service.getSuspendedUsers()).isEqualTo(3L);
        assertThat(service.getNewUsersToday()).isEqualTo(7L);

        // getNewUsersToday는 오늘 0시(고정 clock 기준)를 경계로 조회
        LocalDateTime expectedStart = LocalDate.now(fixedClock).atStartOfDay();
        then(accountRepository).should().countByCreatedAtGreaterThanEqual(expectedStart);
    }

    @Test
    @DisplayName("신규 가입 오늘 집계: UTC 자정 넘겨도 KST 날짜 경계로 조회 (KAN-181 S03)")
    void getNewUsersToday_usesKstDateBoundary() {
        // 2026-06-01T16:00:00Z = KST 2026-06-02T01:00 → '오늘'은 KST 기준 2026-06-02.
        // Clock 빈은 systemUTC지만 created_at은 KST로 기록되므로 KST 날짜 경계로 조회해야 정합.
        Clock kstBoundaryClock = Clock.fixed(Instant.parse("2026-06-01T16:00:00Z"), ZoneOffset.UTC);
        AdminUserService service = new AdminUserService(accountRepository, reportRepository, kstBoundaryClock);
        given(accountRepository.countByCreatedAtGreaterThanEqual(any())).willReturn(1L);

        service.getNewUsersToday();

        // UTC 날짜(2026-06-01)가 아니라 KST 날짜(2026-06-02) 시작을 경계로 조회.
        then(accountRepository).should()
                .countByCreatedAtGreaterThanEqual(LocalDateTime.of(2026, 6, 2, 0, 0));
    }

    @Test
    @DisplayName("정지 호출 시 save 명시 호출 없음 — 영속 컨텍스트 dirty checking 의존")
    void suspend_doesNotCallSave() {
        Account account = activeUser();
        given(accountRepository.findById(1L)).willReturn(Optional.of(account));

        service().suspend(1L, "사유", 1);

        then(accountRepository).should(never()).save(any());
    }
}
