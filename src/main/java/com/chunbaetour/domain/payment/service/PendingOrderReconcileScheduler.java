package com.chunbaetour.domain.payment.service;

import com.chunbaetour.domain.payment.client.PaymentGatewayClient;
import com.chunbaetour.domain.payment.client.PaymentGatewayClient.PortOnePaymentInfo;
import com.chunbaetour.domain.payment.entity.PaymentOrder;
import com.chunbaetour.domain.payment.repository.PaymentOrderRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 고아 PENDING 충전 주문 재조정 스케줄러 (B3).
 *
 * <p><b>왜 필요한가</b>: 충전은 PortOne 웹훅(TRANSACTION_PAID/FAILED)으로 PENDING → COMPLETED/FAILED 종착한다.
 * 웹훅이 미도달(네트워크 단절)하거나 수신 중 서버가 크래시하면 주문이 PENDING으로 영구 잔류한다. 결과:
 * <ul>
 *   <li>실제 결제(PAID)됐는데 엽전 미충전 — 돈만 받고 적립 안 됨(금전 손실).</li>
 *   <li>미결제 PENDING이 잔류 — {@link DailyChargeLimiter}의 당일 한도 SUM에 계속 합산돼 정상 사용자 한도 과점유.</li>
 * </ul>
 * 본 스케줄러가 일정 시간 지난 PENDING을 PG 진실 상태로 조회해 종착 상태로 재조정한다.
 *
 * <p><b>왜 CallbackService 재사용인가</b>: 상태 전이/멱등키 해제/지갑 충전 로직을 웹훅 경로와 단일화한다.
 * {@link CallbackService#handleSuccess}/{@link CallbackService#handleFail}는 DB-level CAS
 * (completeIfNotCompleted/failIfPending)로 멱등하므로, 스케줄러와 웹훅이 같은 주문을 동시에 처리해도
 * row lock으로 직렬화돼 한쪽만 전이에 성공한다 — 중복 적립/이중 해제가 발생하지 않는다. 별도 락 불필요.
 *
 * <p><b>왜 PG 상태를 먼저 보고 분기하나</b>: handleSuccess를 PENDING 전체에 무조건 부르면, 사용자가 결제창에서
 * 결제 진행 중(PG=READY)인 주문이 amountValid=false로 판정돼 failIfPending으로 <b>오판 FAILED</b> 처리된다.
 * 따라서 verifyPayment로 PG 실제 상태를 확인한 뒤에만 종착 전이를 수행한다.
 *
 * <p><b>주기/임계</b>: 5분 fixedDelay. 생성 후 {@code RECONCILE_AGE}(10분) 지난 PENDING만 PG 조회 대상 —
 * 정상 웹훅(수초 내 도달)분은 건드리지 않는다. PG가 여전히 미결제(READY)인 주문은 {@code ABANDON_AGE}(30분)
 * 초과 시 이탈로 보고 FAILED 처리(멱등키 해제 → 한도 합산 제외). 다중 인스턴스는 {@code @SchedulerLock}으로
 * 한 인스턴스만 실행.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingOrderReconcileScheduler {

    /**
     * createdAt 비교 기준 시간대 — KST.
     * createdAt(@CreatedDate)은 JVM 기본존(운영 -Duser.timezone=Asia/Seoul)으로 기록되는 KST wall-clock이고,
     * Clock 빈은 systemUTC라 now(clock)는 UTC다. 그대로 비교하면 9h 어긋나 stale 판정이 깨지므로(고아 주문 영영
     * 미검출), createdAt과 같은 KST naive 값으로 now를 산정한다(DailyChargeLimiter와 동일 정책, KAN-293 참고).
     */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final long SCHEDULE_INTERVAL_MS = 300_000L; // 5분
    /** PG 조회 시작 나이 — 생성 후 이만큼 지난 PENDING만 대상(정상 웹훅 도달분 제외). */
    private static final long RECONCILE_AGE_MINUTES = 10L;
    /** 이탈 판정 나이 — PG가 여전히 미결제(READY)이고 이만큼 지났으면 FAILED로 정리. */
    private static final long ABANDON_AGE_MINUTES = 30L;
    /** 1회 실행당 처리 건수 — 순차 처리로 PG 동시 호출 폭발 방지. */
    private static final int BATCH_SIZE = 20;

    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentGatewayClient paymentGatewayClient;
    private final CallbackService callbackService;
    private final Clock clock;

    @Scheduled(fixedDelay = SCHEDULE_INTERVAL_MS)
    @SchedulerLock(name = "pending_order_reconcile", lockAtMostFor = "PT3M", lockAtLeastFor = "PT30S")
    public void reconcileStalePendingOrders() {
        // createdAt(KST wall-clock)과 동일 프레임으로 now 산정 — UTC now와 비교하면 9h skew로 stale 미검출
        LocalDateTime now = LocalDateTime.now(clock.withZone(KST));
        LocalDateTime threshold = now.minusMinutes(RECONCILE_AGE_MINUTES);

        // 생성 10분 지난 PENDING을 오래된 순으로 조회 (락 없음 — 상태 전이는 CallbackService CAS에 위임)
        List<PaymentOrder> staleOrders =
                paymentOrderRepository.findStalePendingOrders(threshold, PageRequest.of(0, BATCH_SIZE));
        if (staleOrders.isEmpty()) {
            return;
        }
        log.info("[고아 PENDING 재조정] {}건 대상 (기준 시각: {})", staleOrders.size(), now);

        for (PaymentOrder order : staleOrders) {
            reconcileSingle(order, now);
        }
    }

    private void reconcileSingle(PaymentOrder order, LocalDateTime now) {
        String orderUid = order.getOrderUid();
        try {
            // PG 진실 상태 조회 (외부 네트워크 호출, 트랜잭션 밖)
            PortOnePaymentInfo info = paymentGatewayClient.verifyPayment(orderUid);

            if (info.isPaid()) {
                // 실제 결제됨인데 웹훅 미도달 — COMPLETED + 엽전 충전 복구. handleSuccess가 PG 재검증·금액확인·CAS 수행.
                callbackService.handleSuccess(orderUid, info.transactionId());
                log.info("[고아 PENDING 재조정] PAID 복구 완료: orderUid={}", orderUid);
            } else if (info.isFailed() || info.isCancelled()) {
                // PG에서 실패/취소 확정 — FAILED 전이 + 멱등키 해제
                callbackService.handleFail(orderUid);
                log.info("[고아 PENDING 재조정] {} → FAILED 정리: orderUid={}", info.status(), orderUid);
            } else if (order.getCreatedAt().isBefore(now.minusMinutes(ABANDON_AGE_MINUTES))) {
                // PG 여전히 미결제(READY 등)이고 이탈 임계 초과 — 결제창만 띄우고 이탈한 주문으로 보고 FAILED 정리
                callbackService.handleFail(orderUid);
                log.info("[고아 PENDING 재조정] 미결제 이탈({}) → FAILED 정리: orderUid={}", info.status(), orderUid);
            }
            // else: 미결제지만 이탈 임계 전 — 사용자가 아직 결제 중일 수 있어 다음 주기로 보류
        } catch (RuntimeException e) {
            // PG 조회 실패 등 — 해당 건만 건너뛰고 다음 주기 재시도 (배치 전체 중단 방지)
            log.warn("[고아 PENDING 재조정] 처리 실패, 다음 주기 재시도: orderUid={}, 원인={}", orderUid, e.toString());
        }
    }
}
