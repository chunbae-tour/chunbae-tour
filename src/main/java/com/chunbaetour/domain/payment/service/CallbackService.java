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

    // handle()에서 모든 handle* 메서드를 self-invocation으로 호출하므로
    // 각 메서드의 @Transactional이 프록시를 거치지 않음 → handle()의 트랜잭션에 통합.
    // handle* 메서드에 @Transactional을 선언하지 않는다.
    @Transactional(noRollbackFor = PaymentException.class)
    public void handle(String webhookId, WebhookPayload payload) {
        // Standard Webhooks: 동일 webhook-id 재전송 시 즉시 200 리턴 (중복 처리 방지)
        if (!idempotencyService.markWebhookIfAbsent(webhookId)) {
            return;
        }
        scheduleWebhookUnmarkOnRollback(webhookId);

        try {
            WebhookPayload.WebhookData data = payload.data();
            if (data == null) return;

            WebhookEventType eventType = WebhookEventType.from(payload.type());
            switch (eventType) {
                case TRANSACTION_PAID ->
                        handleSuccess(data.paymentId(), data.transactionId());
                case TRANSACTION_FAILED ->
                        handleFail(data.paymentId());
                case TRANSACTION_CANCELLED ->
                        handleCancelled(data.paymentId());
                case TRANSACTION_PARTIAL_CANCELLED ->
                        handlePartialCancelled(data.paymentId());
                // TRANSACTION_CANCEL_PENDING은 취소 진행 중 상태로, 이후 TRANSACTION_CANCELLED로 확정됨.
                // 별도 처리 없이 명시적 무시.
                case TRANSACTION_CANCEL_PENDING -> { }
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
            // DataAccessException 등 예상 외 예외 → 500 반환 → PortOne 자동 재시도.
            // scheduleWebhookUnmarkOnRollback의 afterCompletion(rollback)과 중복 실행되나,
            // Redis delete는 멱등이므로 문제 없음. 명시적 해제로 의도를 코드에 남긴다.
            idempotencyService.unmarkWebhook(webhookId);
            throw e;
        }
    }

    // noRollbackFor: 금액 불일치 시 failIfPending UPDATE 커밋 보장 (throw해도 롤백 안 됨)
    @Transactional(noRollbackFor = PaymentException.class)
    public void handleSuccess(String paymentId, String txId) {
        // 1단계: 락 없이 조회 — 정보 추출(userId/amount/idempotencyKey) + COMPLETED 조기 리턴
        //   기존 2-phase(findByOrderUid → 락 조회)는 Phase 1의 cached PENDING entity가
        //   Phase 2 락 획득 후에도 stale 상태로 반환되어 동시 webhook 2건이 모두 charge() 분기 통과 → 중복 적립.
        //   본 구조는 status 분기를 service가 아닌 DB-level CAS UPDATE에 위임하여 cached entity와 무관하게 멱등성 보장.
        //   userId/amount/idempotencyKey는 immutable 필드이므로 stale 캐시여도 안전.
        PaymentOrder order = paymentOrderRepository.findByOrderUid(paymentId)
                .orElseThrow(() -> new PaymentException(ErrorCode.PAYMENT_HISTORY_NOT_FOUND));

        // COMPLETED 조기 리턴: 도메인 상태 전이상 COMPLETED는 단방향 종착 (이후 REFUNDED는 별도 환불 경로).
        //   cached COMPLETED entity는 DB 현재 상태와 항상 일치 — stale 위험 0이므로 PG 검증 생략 안전.
        //   FAILED는 handleFail 선점 경합일 수 있으므로 계속 진행 (PG PAID 시 CAS UPDATE로 결제 복구).
        //   scheduleUnmark: 이전 처리에서 afterCommit 장애로 키가 남아있을 경우를 대비해 항상 정리.
        if (order.getStatus() == PaymentOrderStatus.COMPLETED) {
            scheduleUnmark(order.getIdempotencyKey());
            return;
        }

        // 2단계: PG 검증 (외부 네트워크 호출, 락 미점유)
        // amountValid에 transactionId 비어있지 않음을 포함 — PG webhook이 비정상 payload 전송 시
        // walletService.charge가 빈 transaction id로 실행되어 결제 추적성 손실 차단. CodeRabbit 지적 반영.
        PortOnePaymentInfo info = paymentGatewayClient.verifyPayment(paymentId);
        boolean amountValid = info.isPaid()
                && info.totalAmount() != null
                && info.totalAmount().equals(order.getAmount())
                && txId != null && !txId.isBlank();

        if (!amountValid) {
            // 3a) PENDING → FAILED 조건부 CAS. 이미 FAILED/COMPLETED/REFUNDED/CANCELLED면 0 반환.
            //     동시 호출 시 한쪽만 1 반환 → idempotencyKey 정확히 1회 해제.
            int failed = paymentOrderRepository.failIfPending(paymentId);
            if (failed == 0) {
                // 다른 스레드/이전 호출이 이미 종착 상태로 전환 완료 — 본 호출은 중복 처리.
                // throw 시 운영 에러 로그만 누적되고 PortOne은 동일 webhook을 재시도 → 무한 noise.
                // 이전 afterCommit 장애로 키가 남아있을 수 있으므로 정리 후 조용히 return.
                scheduleUnmark(order.getIdempotencyKey());
                return;
            }
            scheduleUnmark(order.getIdempotencyKey());
            throw new PaymentException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        // 3b) PENDING/FAILED → COMPLETED 전환 (REFUNDED/CANCELLED는 차단 — Repository WHERE 화이트리스트).
        //     DB-level CAS — 동시 webhook 2건 호출 시 InnoDB row lock 직렬화 → 한쪽만 1 반환.
        //     0 반환 시 다른 스레드/이전 호출이 먼저 종착 전환 — walletService.charge 중복 실행 차단.
        //     이전 afterCommit 장애로 키가 남아있을 수 있으므로 정리 후 return.
        int completed = paymentOrderRepository.completeIfNotCompleted(paymentId, txId);
        if (completed == 0) {
            scheduleUnmark(order.getIdempotencyKey());
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
        // 이전 afterCommit 장애로 키가 남아있을 수 있으므로 정리 후 return.
        int failed = paymentOrderRepository.failIfPending(paymentId);
        if (failed == 0) {
            scheduleUnmark(order.getIdempotencyKey());
            return;
        }

        scheduleUnmark(order.getIdempotencyKey());
    }

    public void handleCancelled(String paymentId) {
        PaymentOrder order = paymentOrderRepository.findByOrderUidWithLock(paymentId)
                .orElseThrow(() -> new PaymentException(ErrorCode.PAYMENT_HISTORY_NOT_FOUND));

        // 이미 종착 상태: 이전 afterCommit 장애로 키가 남아있을 수 있으므로 정리 후 return.
        if (order.getStatus() == PaymentOrderStatus.CANCELLED
                || order.getStatus() == PaymentOrderStatus.REFUNDED
                || order.getStatus() == PaymentOrderStatus.ADJUSTMENT_REQUIRED) {
            scheduleUnmark(order.getIdempotencyKey());
            return;
        }

        if (order.getStatus() == PaymentOrderStatus.PENDING || order.getStatus() == PaymentOrderStatus.FAILED) {
            order.cancel();
            scheduleUnmark(order.getIdempotencyKey());
            return;
        }

        // 여기까지 오는 상태: COMPLETED 또는 PARTIAL_CANCELLED
        long reclaimed = walletService.reclaimAvailableForExternalCancel(
                order.getUserId(),
                order.getAmount(),
                order.getId()
        );
        if (reclaimed == order.getAmount()) {
            order.cancel();
        } else {
            order.requireAdjustment();
        }
        scheduleUnmark(order.getIdempotencyKey());
    }

    public void handlePartialCancelled(String paymentId) {
        PaymentOrder order = paymentOrderRepository.findByOrderUidWithLock(paymentId)
                .orElseThrow(() -> new PaymentException(ErrorCode.PAYMENT_HISTORY_NOT_FOUND));

        // ADJUSTMENT_REQUIRED: 이미 보정 필요 상태 — 추가 처리 불필요
        if (order.getStatus() == PaymentOrderStatus.ADJUSTMENT_REQUIRED) {
            return;
        }

        // PARTIAL_CANCELLED: PortOne at-least-once로 동일 결제에 추가 부분취소 이벤트가 올 수 있음.
        // amount.cancelled는 누적 취소액이라 이전 처리분 delta 계산 불가(별도 추적 필드 없음).
        // 추가 이벤트는 ADJUSTMENT_REQUIRED로 전환해 관리자 확인으로 처리.
        if (order.getStatus() == PaymentOrderStatus.PARTIAL_CANCELLED) {
            order.requireAdjustment();
            return;
        }

        // COMPLETED 이외 상태(PENDING/FAILED/CANCELLED/REFUNDED)는 부분취소 처리 불가
        if (order.getStatus() != PaymentOrderStatus.COMPLETED) {
            return;
        }

        // PG에서 취소 금액 조회 — cancelledAmount가 null이거나 0 이하면 정산 보정 상태로 남김
        PortOnePaymentInfo info = paymentGatewayClient.verifyPayment(paymentId);
        Long cancelledAmount = info.cancelledAmount();
        if (cancelledAmount == null || cancelledAmount <= 0) {
            order.requireAdjustment();
            return;
        }

        // 취소 금액만큼 엽전 회수 (잔액 부족 시 debitUpTo로 가능한 만큼만 회수)
        long reclaimed = walletService.reclaimAvailableForExternalCancel(
                order.getUserId(),
                cancelledAmount,
                order.getId()
        );
        if (reclaimed == cancelledAmount) {
            order.partialCancel();
        } else {
            order.requireAdjustment();
        }
        // 충전 멱등성 키는 handleSuccess에서 이미 해제됨 — 재해제 불필요
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

    private void scheduleWebhookUnmarkOnRollback(String webhookId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    idempotencyService.unmarkWebhook(webhookId);
                }
            }
        });
    }
}
