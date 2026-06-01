package com.chunbaetour.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("탈퇴 계정");
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
    void unsuspend_on_deleted_account_throws() {
        Account account = Account.createForSeed(
                "deletedunsuspend@example.com", "hash", "탈퇴해제", Role.USER, AccountStatus.DELETED);

        assertThatThrownBy(account::unsuspend)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("탈퇴 계정");
    }
}
