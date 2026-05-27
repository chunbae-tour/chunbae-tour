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
    void softDelete_called_twice_throws_IllegalStateException() {
        // 정책: 멱등 skip 아닌 명시적 에러. 중복 호출은 정상 흐름에서 발생할 수 없으므로
        // 도달했다면 호출자 결함 또는 토큰 cascade 우회 → silent로 넘기지 않는다.
        Account account = Account.createForSeed(
                "already@example.com", "hash", "이미탈퇴", Role.USER, AccountStatus.DELETED);

        assertThatThrownBy(() -> account.softDelete(NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 탈퇴");
    }

    @Test
    void softDelete_does_not_mutate_state_when_already_deleted() {
        // 가드 분기는 status 세팅보다 먼저 실행돼야 한다 — 즉 예외 던진 직후 partial 상태가 남으면 안 된다.
        // (status는 이미 DELETED라 의미가 없지만 deletedAt이 NOW로 덮어써지지 않는지 확인.)
        Account account = Account.createForSeed(
                "already@example.com", "hash", "이미탈퇴", Role.USER, AccountStatus.DELETED);

        assertThatThrownBy(() -> account.softDelete(NOW))
                .isInstanceOf(IllegalStateException.class);

        assertThat(account.getStatus()).isEqualTo(AccountStatus.DELETED);
        // createForSeed는 deletedAt을 세팅하지 않으므로 null 유지가 곧 NOW로 덮어쓰지 않았다는 증거.
        assertThat(account.getDeletedAt()).isNull();
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
}
