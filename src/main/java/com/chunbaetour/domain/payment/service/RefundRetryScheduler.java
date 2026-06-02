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
    private static final int RETRY_BATCH_SIZE = 20;

    private final RefundRepository refundRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentGatewayClient paymentGatewayClient;
    private final RefundService refundService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /**
     * 1분마다 실행. 스케줄러 주기(1분) ≠ 재시도 간격.
     * next_retry_at 조건으로 실제 PG 호출은 도래한 건만 처리.
     */
    @Scheduled(fixedDelay = 60_000)
    public void retryFailedRefunds() {
        LocalDateTime now = LocalDateTime.now(clock);
        var pageable = PageRequest.of(0, RETRY_BATCH_SIZE);
        var retryable = refundRepository.findRetryableRefunds(now, MAX_RETRY_COUNT, RefundStatus.FAILED, pageable);

        if (retryable.isEmpty()) {
            return;
        }
        log.info("환불 재시도 스케줄러 실행: {}건 대상", retryable.size());

        for (Refund refund : retryable) {
            processRetry(refund);
        }
    }

    private void processRetry(Refund refund) {
        // 환불 대상 주문의 orderUid 조회
        var order = paymentOrderRepository.findById(refund.getPaymentOrderId()).orElse(null);
        if (order == null) {
            log.warn("환불 재시도 실패: paymentOrderId={} 주문 없음, refundId={}", refund.getPaymentOrderId(), refund.getId());
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
                    completeRetry(refund, orderUid);
                    return;
                }
            } catch (RuntimeException ignored) {
                // PG 상태 조회도 실패 → 다음 재시도로 넘김
            }
            recordFailure(refund);
            log.warn("환불 재시도 실패 (retryCount={}): refundId={}, orderUid={}",
                    refund.getRetryCount() + 1, refund.getId(), orderUid);
            return;
        }

        completeRetry(refund, orderUid);
        log.info("환불 재시도 성공: refundId={}, orderUid={}", refund.getId(), orderUid);
    }

    private void completeRetry(Refund refund, String orderUid) {
        transactionTemplate.executeWithoutResult(status ->
                refundService.completeSchedulerRetry(refund.getId(), orderUid, refund.getAmount()));
    }

    private void recordFailure(Refund refund) {
        transactionTemplate.executeWithoutResult(status -> {
            // detached entity를 DB에서 다시 조회해 Hibernate 변경 추적 활성화
            Refund managed = refundRepository.findById(refund.getId()).orElse(null);
            if (managed == null) return;
            int nextCount = managed.getRetryCount() + 1;
            // 최대 재시도 횟수 초과 시 nextRetryAt을 null로 설정해 스케줄러 대상에서 제외
            LocalDateTime nextRetryAt = nextCount < RETRY_INTERVALS_MINUTES.length
                    ? LocalDateTime.now(clock).plusMinutes(RETRY_INTERVALS_MINUTES[nextCount])
                    : null;
            managed.recordRetryFailure(nextRetryAt);
        });
    }
}
