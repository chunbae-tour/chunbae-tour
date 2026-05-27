package com.chunbaetour.domain.payment.service;

import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.payment.client.PaymentGatewayClient;
import com.chunbaetour.domain.payment.client.PaymentGatewayClient.PortOnePaymentInfo;
import com.chunbaetour.domain.payment.dto.request.WebhookPayload;
import com.chunbaetour.domain.payment.entity.PaymentOrder;
import com.chunbaetour.domain.payment.exception.PaymentException;
import com.chunbaetour.domain.payment.repository.PaymentOrderRepository;
import com.chunbaetour.domain.payment.type.PaymentOrderStatus;
import com.chunbaetour.domain.payment.type.WebhookEventType;
import com.chunbaetour.domain.yeopjeon.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class CallbackService {

    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentGatewayClient paymentGatewayClient;
    private final IdempotencyService idempotencyService;
    private final WalletService walletService;

    // handle()에서 handleSuccess/handleFail을 self-invocation으로 호출하므로
    // 각 메서드의 @Transactional이 프록시를 거치지 않음 → handle()에 통합
    @Transactional(noRollbackFor = PaymentException.class)
    public void handle(String webhookId, WebhookPayload payload) {
        // Standard Webhooks: 동일 webhook-id 재전송 시 즉시 200 리턴 (중복 처리 방지)
        if (!idempotencyService.markWebhookIfAbsent(webhookId)) {
            return;
        }

        try {
            if (payload.data() == null) return;

            WebhookEventType eventType = WebhookEventType.from(payload.type());
            switch (eventType) {
                case TRANSACTION_PAID ->
                        handleSuccess(payload.data().paymentId(), payload.data().txId());
                case TRANSACTION_FAILED, TRANSACTION_CANCELLED ->
                        handleFail(payload.data().paymentId());
                default -> { }
            }
        } catch (PaymentException e) {
            // PAYMENT_SERVICE_UNAVAILABLE(503): 키 해제 후 재전파
            //   → PortOne은 5xx를 서버 일시 장애로 판단해 자동 재시도
            // PAYMENT_AMOUNT_MISMATCH(400) 등 비즈니스 오류: 키 유지
            //   → PortOne은 4xx를 최종 실패로 판단해 재시도 안 함 → 관리자가 직접 확인 필요
            if (e.getErrorCode() == ErrorCode.PAYMENT_SERVICE_UNAVAILABLE) {
                idempotencyService.unmarkWebhook(webhookId);
            }
            throw e;
        } catch (RuntimeException e) {
            // DataAccessException 등 예상 외 예외 → 500 반환 → PortOne 자동 재시도
            idempotencyService.unmarkWebhook(webhookId);
            throw e;
        }
    }

    // noRollbackFor: 금액 불일치 시 failIfPending UPDATE 커밋 보장 (throw해도 롤백 안 됨)
    @Transactional(noRollbackFor = PaymentException.class)
    public void handleSuccess(String paymentId, String txId) {
        // 1단계: 락 없이 조회 — 정보 추출(userId/amount/idempotencyKey) + COMPLETED 조기 리턴
        //   기존 2-phase(findByOrderUid → findByOrderUidWithLock)는 Phase 1의 cached PENDING entity가
        //   Phase 2 락 획득 후에도 stale 상태로 반환되어 동시 webhook 2건이 모두 charge() 분기 통과 → 중복 적립.
        //   본 구조는 status 분기를 service가 아닌 DB-level CAS UPDATE에 위임하여 cached entity와 무관하게 멱등성 보장.
        //   userId/amount/idempotencyKey는 immutable 필드이므로 stale 캐시여도 안전.
        PaymentOrder order = paymentOrderRepository.findByOrderUid(paymentId)
                .orElseThrow(() -> new PaymentException(ErrorCode.PAYMENT_HISTORY_NOT_FOUND));

        // COMPLETED만 조기 리턴: FAILED는 handleFail 선점 경합일 수 있으므로 계속 진행 (PG PAID 시 결제 복구)
        if (order.getStatus() == PaymentOrderStatus.COMPLETED) {
            return;
        }

        // 2단계: PG 검증 (외부 네트워크 호출, 락 미점유)
        PortOnePaymentInfo info = paymentGatewayClient.verifyPayment(paymentId);
        boolean amountValid = info.isPaid()
                && info.totalAmount() != null
                && info.totalAmount().equals(order.getAmount());

        if (!amountValid) {
            // 3a) PENDING → FAILED 조건부 CAS. 이미 FAILED/COMPLETED면 0 반환 (중복 unmark 방지).
            //     동시 호출 시 한쪽만 1 반환 → idempotencyKey 정확히 1회 해제.
            int failed = paymentOrderRepository.failIfPending(paymentId);
            if (failed > 0) {
                scheduleUnmark(order.getIdempotencyKey());
            }
            throw new PaymentException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        // 3b) COMPLETED 아니면 COMPLETED 전환 (PENDING + FAILED 모두 허용 — handleFail 선점 후 PG PAID 시 결제 복구).
        //     DB-level CAS — 동시 webhook 2건 호출 시 InnoDB row lock 직렬화 → 한쪽만 1 반환.
        //     0 반환 시 다른 스레드/이전 호출이 먼저 COMPLETED 전환 — walletService.charge 중복 실행 차단.
        int completed = paymentOrderRepository.completeIfNotCompleted(paymentId, txId);
        if (completed == 0) {
            return;
        }

        walletService.charge(order.getUserId(), order.getAmount(), order.getId());
        scheduleUnmark(order.getIdempotencyKey());
    }

    @Transactional
    public void handleFail(String paymentId) {
        // idempotencyKey 추출용 락 없는 조회 — 상태 분기는 DB CAS에 위임
        PaymentOrder order = paymentOrderRepository.findByOrderUid(paymentId)
                .orElseThrow(() -> new PaymentException(ErrorCode.PAYMENT_HISTORY_NOT_FOUND));

        // PENDING → FAILED 조건부 CAS. 이미 COMPLETED/FAILED/REFUNDED면 0 반환 — 멱등 처리.
        int failed = paymentOrderRepository.failIfPending(paymentId);
        if (failed == 0) {
            return;
        }

        scheduleUnmark(order.getIdempotencyKey());
    }

    /**
     * 멱등성 키 해제를 DB 커밋 이후로 예약.
     *
     * 문제 상황:
     *   트랜잭션 내에서 unmark()를 즉시 호출하면, 그 직후 DB 커밋이 실패해도
     *   Redis 키는 이미 삭제된 상태 → 사용자가 재시도하면 중복 충전 발생 가능.
     *
     * 해결:
     *   Spring의 TransactionSynchronization을 활용해 커밋 확정 시점에만 키 삭제.
     *   afterCommit()은 DB 커밋 성공 후에만 실행되므로, 커밋 실패 시 키가 보존됨
     *   → 포트원이 웹훅 재전송 → 재시도 가능.
     *
     * @Override 이유:
     *   TransactionSynchronization은 Spring 인터페이스로, afterCommit()은 default 메서드.
     *   @Override로 "인터페이스 메서드를 재정의한다"는 의도를 명시하고
     *   메서드명 오타 시 컴파일 오류로 잡아줌.
     *
     * else 분기:
     *   트랜잭션 컨텍스트 밖(테스트, 비동기 등)에서 호출된 경우 즉시 unmark.
     */
    private void scheduleUnmark(String idempotencyKey) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // 현재 트랜잭션에 콜백 등록 — 커밋 완료 시 afterCommit() 실행됨
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    idempotencyService.unmark(idempotencyKey);
                }
            });
        } else {
            // 트랜잭션 밖 → 즉시 실행
            idempotencyService.unmark(idempotencyKey);
        }
    }
}
