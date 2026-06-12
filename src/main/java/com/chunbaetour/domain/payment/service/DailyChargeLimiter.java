package com.chunbaetour.domain.payment.service;

import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.payment.exception.PaymentException;
import com.chunbaetour.domain.payment.repository.PaymentOrderRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 일일 충전 총액 제한 (KAN-293).
 * 사용자별 하루 충전 시도 누적액을 {@code payment_orders}에서 직접 SUM 조회해 일 한도(50만원) 초과를 차단한다.
 *
 * <p><b>진실원은 payment_orders 테이블이다.</b> 별도 카운터(Redis 등)를 두지 않고 검증 시점에 당일 주문을 합산한다.
 * 집계 대상은 진행중(PENDING) + 완료 이력이 있는 주문(pgTransactionId != null)이며, 상세 기준은
 * {@link PaymentOrderRepository#sumChargedAmountForDailyLimit}에 정의돼 있다.
 *
 * <ul>
 *   <li><b>PENDING 포함</b>: 결제창은 떴지만 아직 미완료인 주문도 합산 → 새 멱등키로 N건을 만들어 한도를 우회하는
 *       경로를 차단한다(별도 카운터 방식의 핵심 결함 해소).</li>
 *   <li><b>완료 후 취소·환불 유지</b>: pgTransactionId 마커로 "한 번이라도 완료된" 주문을 계속 합산 → 50만 충전 후
 *       취소·재충전으로 한도를 세탁하는 것을 막는다.</li>
 *   <li><b>결제 전 취소·실패 제외</b>: PENDING→CANCELLED(사용자 취소)·FAILED는 pgTransactionId가 null이라
 *       합산에서 자연 제외 → 결제를 포기한 사용자가 한도를 억울하게 소모하지 않는다.</li>
 * </ul>
 *
 * <p>한도 일자는 KST 자정 경계로 리셋한다. created_at은 UTC 저장이므로 KST 영업일 경계를 UTC로 변환해 조회한다.
 * 검증·집계가 모두 created_at 단일 기준이라 자정 경계에서 일자 불일치가 발생하지 않는다.
 *
 * <p>Redis 의존이 없어 fail-open/누적 유실 같은 경로가 존재하지 않는다. DB 장애 시에는 충전 플로우 전체가
 * 동일하게 실패하므로 한도만 조용히 비활성화되는 상황도 없다.
 */
@Component
@RequiredArgsConstructor
public class DailyChargeLimiter {

    /** 일일 충전 누적 한도(원). 건당 한도(5,000~100,000)와 별개로 그 위에 적용된다. */
    static final long DAILY_LIMIT = 500_000L;
    /** 충전 일자 판단 기준 시간대 — 한국 자정 경계로 일일 한도를 리셋한다. */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final PaymentOrderRepository paymentOrderRepository;
    private final Clock clock;

    /**
     * 충전 요청 시 일일 한도를 검증한다. 오늘 충전 누적액 + 이번 요청액이 한도를 넘으면 차단한다.
     *
     * @throws PaymentException PAY_030 — 일일 충전 한도 초과
     */
    public void assertWithinDailyLimit(Long userId, long amount) {
        // KST 영업일 경계(오늘 00:00 ~ 내일 00:00)를 DB 저장 기준 UTC LocalDateTime으로 변환한다.
        LocalDate today = LocalDate.now(clock.withZone(KST));
        ZonedDateTime startKst = today.atStartOfDay(KST);
        LocalDateTime startAt = startKst.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime endAt = startKst.plusDays(1).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();

        // 당일 충전 누적액(진행중 + 완료 이력)을 payment_orders에서 직접 합산한다.
        long charged = paymentOrderRepository.sumChargedAmountForDailyLimit(userId, startAt, endAt);
        if (charged + amount > DAILY_LIMIT) {
            throw new PaymentException(ErrorCode.DAILY_CHARGE_LIMIT_EXCEEDED);
        }
    }
}
