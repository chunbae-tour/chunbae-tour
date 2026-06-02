package com.chunbaetour.domain.payment.service;

import com.chunbaetour.domain.payment.client.PaymentGatewayClient;
import com.chunbaetour.domain.payment.client.PaymentGatewayClient.PortOnePaymentInfo;
import com.chunbaetour.domain.payment.entity.Refund;
import com.chunbaetour.domain.payment.repository.PaymentOrderRepository;
import com.chunbaetour.domain.payment.repository.RefundRepository;
import com.chunbaetour.domain.payment.type.RefundStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefundRetryScheduler {

    /**
     * 지수 백오프 정책: 재시도 횟수에 따라 다음 시도 간격을 점진적으로 늘린다.
     * - 첫 재시도(1분): 충전 직후 실수로 빠른 환불을 기대하는 사용자 경험 고려.
     * - 이후 간격 증가: 대부분의 PG 일시 장애가 5분 내 해결되고, 그 이후는 더 심각한 장애일 가능성이 높음.
     * - 서버/PG 부하 분산: 장애 상황에서 FAILED 건이 쌓여도 동시에 몰리지 않도록 간격을 늘린다.
     * - 5회 초과 시 자동 재시도 중단 → 관리자가 직접 확인.
     */
    private static final int[] RETRY_INTERVALS_MINUTES = {1, 5, 15, 60, 240};
    private static final int MAX_RETRY_COUNT = 5;

    // 한 번 실행에 처리할 최대 건수 — 병렬 처리 없이 순차 실행해 PG 동시 호출 폭발 방지
    private static final int BATCH_SIZE = 20;

    private final RefundRepository refundRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentGatewayClient paymentGatewayClient;
    private final RefundService refundService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /**
     * PENDING 환불 최초 처리.
     * requestRefund()에서 PG 호출 없이 PENDING으로 접수된 환불을 1분마다 처리한다.
     * 처음부터 cancelPayment를 배치로 처리해 API 응답 속도와 장애 격리를 보장한다.
     */
    @Scheduled(fixedDelay = 60_000)
    public void processPendingRefunds() {
        var pageable = PageRequest.of(0, BATCH_SIZE);
        var pending = refundRepository.findByStatusOrderByCreatedAt(RefundStatus.PENDING, pageable);

        if (pending.isEmpty()) {
            return;
        }
        log.info("환불 처리 스케줄러 실행: {}건 대상", pending.size());

        for (Refund refund : pending) {
            processSingleRefund(refund);
        }
    }

    /**
     * FAILED 환불 재시도.
     * 지수 백오프 정책: next_retry_at 조건으로 실제 PG 호출은 도래한 건만 처리.
     * 스케줄러 주기(1분) ≠ 재시도 간격.
     */
    @Scheduled(fixedDelay = 60_000)
    public void retryFailedRefunds() {
        LocalDateTime now = LocalDateTime.now(clock);
        var pageable = PageRequest.of(0, BATCH_SIZE);
        var retryable = refundRepository.findRetryableRefunds(now, MAX_RETRY_COUNT, RefundStatus.FAILED, pageable);

        if (retryable.isEmpty()) {
            return;
        }
        log.info("환불 재시도 스케줄러 실행: {}건 대상", retryable.size());

        for (Refund refund : retryable) {
            processSingleRefund(refund);
        }
    }

    private void processSingleRefund(Refund refund) {
        var order = paymentOrderRepository.findById(refund.getPaymentOrderId()).orElse(null);
        if (order == null) {
            log.warn("환불 처리 실패: paymentOrderId={} 주문 없음, refundId={}",
                    refund.getPaymentOrderId(), refund.getId());
            return;
        }

        String orderUid = order.getOrderUid();
        try {
            paymentGatewayClient.cancelPayment(
                    orderUid, refund.getAmount(), refund.getReason(), "refund-" + refund.getId());
        } catch (RuntimeException e) {
            // cancelPayment 실패 시 PG 상태 재확인 (타임아웃 후 실제로 취소됐을 수 있음)
            try {
                PortOnePaymentInfo info = paymentGatewayClient.verifyPayment(orderUid);
                if (info.isCancelled()) {
                    completeRefund(refund, orderUid);
                    return;
                }
            } catch (RuntimeException ignored) {
                // PG 상태 조회도 실패 → 다음 시도로 넘김
            }
            recordFailure(refund);
            log.warn("환불 PG 호출 실패 (retryCount={}): refundId={}, orderUid={}",
                    refund.getRetryCount() + 1, refund.getId(), orderUid);
            return;
        }

        completeRefund(refund, orderUid);
        log.info("환불 처리 완료: refundId={}, orderUid={}", refund.getId(), orderUid);
    }

    private void completeRefund(Refund refund, String orderUid) {
        transactionTemplate.executeWithoutResult(status ->
                refundService.completeSchedulerRetry(refund.getId(), orderUid, refund.getAmount()));
    }

    private void recordFailure(Refund refund) {
        transactionTemplate.executeWithoutResult(status -> {
            // detached entity를 DB에서 다시 조회해 Hibernate 변경 추적 활성화
            Refund managed = refundRepository.findById(refund.getId()).orElse(null);
            if (managed == null) return;

            if (managed.getStatus() == RefundStatus.PENDING) {
                // PENDING 첫 번째 PG 실패 → FAILED 전환, 첫 재시도 1분 후 예약
                LocalDateTime firstRetryAt = LocalDateTime.now(clock).plusMinutes(RETRY_INTERVALS_MINUTES[0]);
                managed.fail("PORTONE_CANCEL_FAILED", firstRetryAt);
            } else if (managed.getStatus() == RefundStatus.FAILED) {
                // FAILED 재시도 실패 → retryCount 증가, 다음 재시도 시각 갱신
                // 최대 재시도 횟수 초과 시 nextRetryAt=null → 스케줄러 대상에서 영구 제외
                int nextCount = managed.getRetryCount() + 1;
                LocalDateTime nextRetryAt = nextCount < RETRY_INTERVALS_MINUTES.length
                        ? LocalDateTime.now(clock).plusMinutes(RETRY_INTERVALS_MINUTES[nextCount])
                        : null;
                managed.recordRetryFailure(nextRetryAt);
            }
        });
    }
}
