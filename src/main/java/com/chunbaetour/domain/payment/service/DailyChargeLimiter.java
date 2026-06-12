package com.chunbaetour.domain.payment.service;

import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.payment.exception.PaymentException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 일일 충전 총액 제한 (KAN-293).
 * 사용자별 하루 <b>충전 완료</b> 누적액을 Redis에 쌓고, 충전 요청 시 일 한도(50만원) 초과를 차단한다.
 *
 * <p>누적은 충전 완료(웹훅 COMPLETED) 시점에만 가산하므로 실패·취소 충전은 한도에 반영되지 않는다.
 * Redis 키 {@code daily:charge:{userId}:{KST날짜}} 에 누적액을 저장하고 TTL 48시간으로 자동 청소한다.
 * 날짜가 키에 포함되므로 자정이 지나면 자동으로 새 키(새 한도)가 적용된다.
 *
 * <p>Redis 장애 시에는 결제 가용성을 우선해 한도 검증·누적을 건너뛰고 통과시킨다(경고 로그만 남김).
 *
 * <p><b>알려진 한계(TOCTOU) — 의도적 허용</b>: 검증(조회)과 누적(완료 시 가산)이 분리돼 있어,
 * 잔여 한도가 1만원인 사용자가 동시에 1만원 충전 2건을 요청하면 둘 다 검증을 통과한 뒤 모두 완료돼
 * 한도를 일시적으로 소폭 초과할 수 있다. 이를 완벽히 막으려면 결제창 진입 시 한도를 선차감하는
 * Pending Amount(임시 차감 후 만료 복구) 관리가 필요하나, 사용자가 결제를 중도 포기하면 하루 한도를
 * 억울하게 소모하게 된다. 일 한도는 음수 불가 자원(재고)이 아니라 남용 방지용 soft cap이므로,
 * 결제 이탈 방지를 우선해 후차감(완료 시 가산) 방식을 유지하고 이 경합은 허용한다.
 * 엄격 차단이 필요해지면 INCR 선점 + 보상 또는 Pending Amount 방식으로 전환한다(별도 STORY).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyChargeLimiter {

    /** 일일 충전 완료 누적 한도(원). 건당 한도(5,000~100,000)와 별개로 그 위에 적용된다. */
    static final long DAILY_LIMIT = 500_000L;
    /** 키 자동 청소용 TTL. 날짜 키라 정밀 만료가 불필요하므로 하루 보존 + 여유를 둔 고정 48시간. */
    private static final Duration KEY_TTL = Duration.ofHours(48);
    /** 충전 일자 판단 기준 시간대 — 한국 자정 경계로 일일 한도를 리셋한다. */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String KEY_PREFIX = "daily:charge:";

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;

    /**
     * 충전 요청 시 일일 한도를 검증한다. 오늘 완료 누적액 + 이번 요청액이 한도를 넘으면 차단한다.
     * Redis 장애 시 가용성을 우선해 검증을 건너뛰고 통과시킨다.
     *
     * @throws PaymentException PAY_030 — 일일 충전 한도 초과
     */
    public void assertWithinDailyLimit(Long userId, long amount) {
        long charged;
        try {
            String value = redisTemplate.opsForValue().get(key(userId));
            charged = (value == null) ? 0L : Long.parseLong(value);
        } catch (Exception e) {
            // Redis 장애·파싱 오류 시 결제 가용성 우선 — 한도 검증 생략하고 통과
            log.warn("[일일 충전 한도] 누적액 조회 실패 — 한도 검증 생략 (userId: {})", userId, e);
            return;
        }
        if (charged + amount > DAILY_LIMIT) {
            throw new PaymentException(ErrorCode.DAILY_CHARGE_LIMIT_EXCEEDED);
        }
    }

    /**
     * 충전 완료 시 오늘 누적액에 충전 금액을 가산한다.
     * 매 가산 후 TTL을 다시 설정해 TTL 없는 잔존 키가 생기지 않도록 한다(날짜 키라 갱신돼도 안전).
     * Redis 장애 시 누적을 포기한다 — 한도가 일시적으로 느슨해질 뿐 결제 자체는 정상 처리된다.
     */
    public void accumulate(Long userId, long amount) {
        try {
            String key = key(userId);
            redisTemplate.opsForValue().increment(key, amount);
            redisTemplate.expire(key, KEY_TTL);
        } catch (Exception e) {
            log.warn("[일일 충전 한도] 누적 가산 실패 — 무시 (userId: {}, amount: {})", userId, amount, e);
        }
    }

    /** daily:charge:{userId}:{KST 일자} */
    private String key(Long userId) {
        LocalDate today = LocalDate.now(clock.withZone(KST));
        return KEY_PREFIX + userId + ":" + today.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }
}
