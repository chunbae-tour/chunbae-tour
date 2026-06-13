package com.chunbaetour.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.payment.exception.PaymentException;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 일일 충전 한도의 타임존 정합성 실DB 가드 (KAN-293 리뷰 C1).
 *
 * <p>mock 단위 테스트는 limiter가 repository에 넘기는 경계값만 검증할 뿐, created_at이 실제로 어떤 존으로
 * 저장되는지는 대조하지 못한다. created_at은 JPA auditing이 JVM 기본 존(테스트/운영 모두 동일 JVM)으로 기록하는
 * wall-clock이므로, limiter의 KST 영업일 경계와 같은 기준이어야 한다. 경계를 UTC로 -9h 변환하던 회귀가
 * 재발하면 "어제 저녁 충전이 오늘로 합산"되어 아래 첫 테스트가 깨진다.
 *
 * <p><b>결정성</b>: limiter의 "오늘"은 주입된 {@link Clock}으로 계산되므로, 테스트 데이터의 날짜도 동일한
 * 고정 Clock에서 산출한다({@link #FIXED_INSTANT}). 실시각 {@code LocalDate.now()}를 쓰면 KST 자정 직전
 * 데이터 삽입과 limiter 호출 사이에 날짜가 바뀌어 회귀와 무관하게 흔들릴 수 있어 고정한다 (KAN-293 리뷰 반영).
 */
@SpringBootTest
class DailyChargeLimiterIntegrationTest extends AbstractIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final long USER_ID = 9001L;
    /** 고정 기준 시각 — UTC 03:00 = KST 12:00 (자정 경계에서 멀어 날짜 경합 없음). */
    private static final Instant FIXED_INSTANT = Instant.parse("2026-06-12T03:00:00Z");

    /** limiter가 사용하는 Clock 빈을 고정값으로 덮어, 테스트 데이터와 limiter가 같은 "오늘"을 보게 한다. */
    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        }
    }

    @Autowired
    private DailyChargeLimiter dailyChargeLimiter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM payment_orders WHERE user_id = ?", USER_ID);
    }

    @Test
    @DisplayName("타임존 가드 — 어제 저녁(KST) 완료 충전은 오늘 한도에 합산되지 않는다 (UTC -9h 회귀 시 깨짐)")
    void yesterdayEveningCharge_notCountedTowardToday() {
        // limiter와 동일한 고정 Clock에서 KST 오늘을 산출 — 자정 경합·고정Clock 불일치 제거
        LocalDate todayKst = LocalDate.now(Clock.fixed(FIXED_INSTANT, KST));
        // 어제 20:00 KST 완료 충전 50만. UTC -9h 경계([어제15:00, 오늘15:00)) 버그면 오늘로 잘못 포함된다.
        insertCompletedCharge(500_000L, todayKst.minusDays(1).atTime(20, 0));

        // 올바른 KST 경계라면 어제 건은 제외 → 오늘 누적 0 → 10만 추가해도 통과
        assertThatCode(() -> dailyChargeLimiter.assertWithinDailyLimit(USER_ID, 100_000L))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("타임존 가드 — 오늘 저녁(KST) 완료 충전은 오늘 한도에 합산된다")
    void todayEveningCharge_countedTowardToday() {
        // limiter와 동일한 고정 Clock에서 KST 오늘을 산출
        LocalDate todayKst = LocalDate.now(Clock.fixed(FIXED_INSTANT, KST));
        // 오늘 20:00 KST 완료 충전 45만 — UTC 경계 버그면 오늘 윈도우(끝 15:00) 밖이라 누락된다.
        insertCompletedCharge(450_000L, todayKst.atTime(20, 0));

        // 45만 + 10만 = 55만 > 50만 → 차단되어야 정상
        assertThatThrownBy(() -> dailyChargeLimiter.assertWithinDailyLimit(USER_ID, 100_000L))
                .isInstanceOf(PaymentException.class)
                .extracting(e -> ((PaymentException) e).getErrorCode())
                .isEqualTo(ErrorCode.DAILY_CHARGE_LIMIT_EXCEEDED);
    }

    /** auditing을 우회해 created_at을 직접 지정하기 위해 JDBC로 완료 충전 행을 삽입한다. */
    private void insertCompletedCharge(long amount, LocalDateTime createdAt) {
        jdbcTemplate.update("""
                INSERT INTO payment_orders
                  (created_at, updated_at, amount, idempotency_key, order_uid,
                   payment_method, pg_transaction_id, status, user_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                createdAt, createdAt, amount,
                "idem-" + UUID.randomUUID(), UUID.randomUUID().toString(),  // order_uid는 varchar(36) — UUID 그대로
                "CARD", "tx-" + UUID.randomUUID(), "COMPLETED", USER_ID);
    }
}
