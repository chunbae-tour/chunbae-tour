package com.chunbaetour.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.report.entity.SanctionType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * {@link Account} 도메인 단위 테스트.
 *
 * <p>본 클래스는 Spring 컨텍스트 없이 도메인 메서드의 상태 전이만 검증한다.
 * Persistence/SQLRestriction 동작은 통합 테스트({@code AccountWithdrawalIntegrationTest})에서 커버.
 *
 * <p><b>패키지 위치 강제</b>: {@link Account#createForSeed}가 {@code package-private}이므로
 * 본 테스트는 {@code com.chunbaetour.domain.auth}에 위치해야 컴파일된다. {@link AccountSeedFactory}와 동일.
 */
class AccountTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 27, 12, 0, 0);

    // ===== Epic C S1 (KAN-143) — softDelete =====

    @Test
    void softDelete_sets_deletedAt_and_status_DELETED() {
        // 회원가입 흐름과 동일한 ACTIVE 상태에서 출발 — 운영 정상 흐름 검증
        Account account = Account.registerUser("withdraw@example.com", "hash", "탈퇴자");
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getDeletedAt()).isNull();

        account.softDelete(NOW);

        // 두 필드는 같은 호출에서 함께 세팅돼야 한다 — @SQLRestriction(deletedAt) + status(DELETED) 짝
        assertThat(account.getStatus()).isEqualTo(AccountStatus.DELETED);
        assertThat(account.getDeletedAt()).isEqualTo(NOW);
    }

    @Test
    void softDelete_called_twice_throws_IllegalStateException_and_preserves_first_deletedAt() {
        // 정책: 멱등 skip 아닌 명시적 에러. 도메인 상태 가드.
        // 픽스처: 실제 정상 탈퇴 흐름 재현 — ACTIVE에서 출발 → 첫 softDelete로 status=DELETED + deletedAt 세팅
        // → 두 번째 softDelete가 IllegalStateException throw + 첫 deletedAt 값이 덮어써지지 않음을 검증
        // (PR #207 hyeonmin02 🔵 review — DELETED + deletedAt=null 픽스처는 실제 운영 상태와 다름).
        LocalDateTime firstDeletedAt = LocalDateTime.of(2026, 5, 27, 10, 0, 0);
        LocalDateTime secondAttemptAt = LocalDateTime.of(2026, 5, 27, 11, 0, 0);
        Account account = Account.registerUser("already@example.com", "hash", "이미탈퇴");
        account.softDelete(firstDeletedAt);

        assertThatThrownBy(() -> account.softDelete(secondAttemptAt))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 탈퇴");

        // 가드가 status/deletedAt 세팅보다 먼저 실행되어 첫 호출 값이 보존되어야 함 — partial 전이 방지.
        assertThat(account.getStatus()).isEqualTo(AccountStatus.DELETED);
        assertThat(account.getDeletedAt()).isEqualTo(firstDeletedAt);
    }

    @Test
    void softDelete_from_SUSPENDED_state_is_allowed() {
        // SUSPENDED → DELETED는 정상 전이 (정지 후 탈퇴는 운영상 가능한 시나리오)
        Account account = Account.createForSeed(
                "suspended@example.com", "hash", "정지자", Role.USER, AccountStatus.SUSPENDED);

        account.softDelete(NOW);

        assertThat(account.getStatus()).isEqualTo(AccountStatus.DELETED);
        assertThat(account.getDeletedAt()).isEqualTo(NOW);
    }

    @Test
    void softDelete_with_null_now_throws_IllegalArgumentException() {
        // 도메인 불변식: status=DELETED ⇔ deletedAt != null. now가 null이면 partial 상태 위험
        // (@SQLRestriction이 행을 살아있는 것으로 잘못 인식).
        // PR #207 CodeRabbit 리뷰 반영 — 호출자가 안전해도 도메인 자체에서 방어.
        Account account = Account.registerUser("nullguard@example.com", "hash", "가드");

        assertThatThrownBy(() -> account.softDelete(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null일 수 없습니다");

        // 가드가 status 세팅보다 먼저 실행돼야 함 — partial 전이 차단 검증
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getDeletedAt()).isNull();
    }

    // ===== KAN-180 Admin S02 — 운영자 정지/해제 =====

    @Test
    void suspend_with_reason_and_until_sets_status_and_fields() {
        Account account = Account.registerUser("suspend@example.com", "hash", "정지대상");

        account.suspend("욕설 신고 누적", NOW);

        assertThat(account.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(account.getSuspendedReason()).isEqualTo("욕설 신고 누적");
        assertThat(account.getSuspendedUntil()).isEqualTo(NOW);
    }

    @Test
    void suspend_with_null_until_is_indefinite() {
        Account account = Account.registerUser("perma@example.com", "hash", "무기한");

        account.suspend("반복 위반", null);

        assertThat(account.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(account.getSuspendedReason()).isEqualTo("반복 위반");
        assertThat(account.getSuspendedUntil()).isNull();
    }

    @Test
    void suspend_on_already_suspended_updates_reason_and_until_without_exception() {
        // 재정지 = 갱신 허용 (IllegalStateException 던지지 않음). 사유/기간 덮어쓰기.
        Account account = Account.registerUser("resuspend@example.com", "hash", "재정지");
        LocalDateTime firstUntil = LocalDateTime.of(2026, 5, 28, 0, 0, 0);
        account.suspend("1차 사유", firstUntil);

        LocalDateTime secondUntil = LocalDateTime.of(2026, 6, 30, 0, 0, 0);
        account.suspend("연장 사유", secondUntil);

        assertThat(account.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(account.getSuspendedReason()).isEqualTo("연장 사유");
        assertThat(account.getSuspendedUntil()).isEqualTo(secondUntil);
    }

    @Test
    void suspend_with_reason_on_deleted_account_throws() {
        Account account = Account.createForSeed(
                "deleted@example.com", "hash", "탈퇴자", Role.USER, AccountStatus.DELETED);

        assertThatThrownBy(() -> account.suspend("사유", NOW))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_ALREADY_DELETED);
    }

    @Test
    void unsuspend_resets_status_and_clears_fields() {
        Account account = Account.registerUser("unsuspend@example.com", "hash", "해제대상");
        account.suspend("사유", NOW);

        account.unsuspend();

        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getSuspendedReason()).isNull();
        assertThat(account.getSuspendedUntil()).isNull();
    }

    @Test
    void unsuspend_after_system_sanction_clears_sanction_fields() {
        // 시스템 제재 중 관리자 수동 해제 → sanctionType/sanctionEndAt 잔류 방지
        // 스케줄러가 ACTIVE 계정의 stale 필드를 clearSystemSanction으로 재처리하는 버그 차단
        Account account = Account.registerUser("sys-unsuspend@example.com", "hash", "시스템해제");
        account.applySystemSanction(SanctionType.SUSPEND_7D, NOW.plusDays(7));

        account.unsuspend();

        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getSanctionType()).isNull();
        assertThat(account.getSanctionEndAt()).isNull();
    }

    @Test
    void unsuspend_on_deleted_account_throws() {
        Account account = Account.createForSeed(
                "deletedunsuspend@example.com", "hash", "탈퇴해제", Role.USER, AccountStatus.DELETED);

        assertThatThrownBy(account::unsuspend)
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_ALREADY_DELETED);
    }

    // ===== PR1+PR2 — 시스템 자동 제재 (KAN-sanction) =====

    @Test
    void applySystemSanction_WARNING_does_not_change_status() {
        Account account = Account.registerUser("warn@example.com", "hash", "경고대상");
        account.applySystemSanction(SanctionType.WARNING, NOW);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getSanctionType()).isNull();
    }

    @Test
    void applySystemSanction_NONE_does_not_change_status() {
        Account account = Account.registerUser("none@example.com", "hash", "제재없음");
        account.applySystemSanction(SanctionType.NONE, null);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getSanctionType()).isNull();
    }

    @Test
    void applySystemSanction_SUSPEND_7D_sets_SUSPENDED_and_fields() {
        Account account = Account.registerUser("s7d@example.com", "hash", "7일정지");
        LocalDateTime endAt = NOW.plusDays(7);
        account.applySystemSanction(SanctionType.SUSPEND_7D, endAt);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(account.getSanctionType()).isEqualTo(SanctionType.SUSPEND_7D);
        assertThat(account.getSanctionEndAt()).isEqualTo(endAt);
    }

    @Test
    void applySystemSanction_PERMANENT_sets_SUSPENDED_and_null_sanctionEndAt() {
        Account account = Account.registerUser("perm-apply@example.com", "hash", "영구정지대상");
        account.applySystemSanction(SanctionType.PERMANENT, null);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(account.getSanctionType()).isEqualTo(SanctionType.PERMANENT);
        assertThat(account.getSanctionEndAt()).isNull();
    }

    @Test
    void applySystemSanction_does_not_downgrade_existing_higher_sanction() {
        // 크로스도메인 집계가 낮은 maxType으로 applySystemSanction 호출 시 기존 높은 제재 보호
        Account account = Account.registerUser("nodeg@example.com", "hash", "다운그레이드방지");
        account.applySystemSanction(SanctionType.PERMANENT, null);

        // PERMANENT 계정에 SUSPEND_7D 시도 → 무시
        account.applySystemSanction(SanctionType.SUSPEND_7D, NOW.plusDays(7));

        assertThat(account.getSanctionType()).isEqualTo(SanctionType.PERMANENT);
        assertThat(account.getSanctionEndAt()).isNull();
    }

    @Test
    void applySystemSanction_does_not_override_manual_suspension() {
        // 관리자 수동 정지(sanctionType=null) 계정에 시스템 제재 시도 → 무시
        // 방치 시 스케줄러가 7일 후 clearSystemSanction()으로 관리자 무기한 정지를 해제하는 버그 방지
        Account account = Account.registerUser("manual-susp@example.com", "hash", "수동정지");
        account.suspend("욕설 신고", null);

        account.applySystemSanction(SanctionType.SUSPEND_7D, NOW.plusDays(7));

        assertThat(account.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(account.getSanctionType()).isNull();
        assertThat(account.getSanctionEndAt()).isNull();
    }

    @Test
    void applySystemSanction_on_DELETED_is_noop() {
        Account account = Account.createForSeed(
                "delsanction@example.com", "hash", "탈퇴제재", Role.USER, AccountStatus.DELETED);
        account.applySystemSanction(SanctionType.SUSPEND_7D, NOW.plusDays(7));
        assertThat(account.getStatus()).isEqualTo(AccountStatus.DELETED);
        assertThat(account.getSanctionType()).isNull();
    }

    @Test
    void clearSystemSanction_resets_status_and_sanction_fields() {
        Account account = Account.registerUser("clear@example.com", "hash", "제재해제");
        account.applySystemSanction(SanctionType.SUSPEND_7D, NOW.plusDays(7));
        account.clearSystemSanction();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getSanctionType()).isNull();
        assertThat(account.getSanctionEndAt()).isNull();
    }

    @Test
    void clearSystemSanction_on_PERMANENT_throws_IllegalStateException() {
        Account account = Account.registerUser("perm@example.com", "hash", "영구정지");
        account.applySystemSanction(SanctionType.PERMANENT, null);
        assertThatThrownBy(account::clearSystemSanction)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PERMANENT");
        assertThat(account.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
    }

    @Test
    void clearSystemSanction_on_DELETED_is_noop() {
        Account account = Account.createForSeed(
                "delclear@example.com", "hash", "탈퇴해제무시", Role.USER, AccountStatus.DELETED);
        account.clearSystemSanction();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.DELETED);
    }

    @Test
    void isSystemSanctionExpired_returns_true_when_endedAt_before_now() {
        Account account = Account.registerUser("expired@example.com", "hash", "만료됨");
        account.applySystemSanction(SanctionType.SUSPEND_7D, NOW.minusHours(1));
        assertThat(account.isSystemSanctionExpired(NOW)).isTrue();
    }

    @Test
    void isSystemSanctionExpired_returns_false_when_not_yet_expired() {
        Account account = Account.registerUser("notexp@example.com", "hash", "미만료");
        account.applySystemSanction(SanctionType.SUSPEND_7D, NOW.plusHours(1));
        assertThat(account.isSystemSanctionExpired(NOW)).isFalse();
    }

    @Test
    void isSystemSanctionExpired_returns_false_when_no_sanction() {
        Account account = Account.registerUser("nosanction@example.com", "hash", "제재없음2");
        assertThat(account.isSystemSanctionExpired(NOW)).isFalse();
    }

    @Test
    void isSystemSanctionExpired_returns_false_for_PERMANENT_null_endedAt() {
        Account account = Account.registerUser("permexp@example.com", "hash", "영구만료불가");
        account.applySystemSanction(SanctionType.PERMANENT, null);
        assertThat(account.isSystemSanctionExpired(NOW)).isFalse();
    }
}
