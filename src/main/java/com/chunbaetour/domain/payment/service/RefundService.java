package com.chunbaetour.domain.payment.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import com.chunbaetour.domain.payment.client.PaymentGatewayClient;
import com.chunbaetour.domain.payment.client.PaymentGatewayClient.PortOnePaymentInfo;
import com.chunbaetour.domain.payment.dto.request.RefundRequest;
import com.chunbaetour.domain.payment.dto.response.RefundResponse;
import com.chunbaetour.domain.payment.dto.response.UserRefundResponse;
import com.chunbaetour.domain.payment.entity.PaymentOrder;
import com.chunbaetour.domain.payment.entity.Refund;
import com.chunbaetour.domain.payment.exception.PaymentException;
import com.chunbaetour.domain.payment.repository.PaymentOrderRepository;
import com.chunbaetour.domain.payment.repository.RefundRepository;
import com.chunbaetour.domain.payment.type.PaymentOrderStatus;
import com.chunbaetour.domain.payment.type.RefundStatus;
import com.chunbaetour.domain.yeopjeon.entity.Wallet;
import com.chunbaetour.domain.yeopjeon.repository.WalletRepository;
import com.chunbaetour.domain.yeopjeon.service.WalletService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefundService {

    private static final int REFUND_PERIOD_DAYS = 7;
    private static final String UK_REFUNDS_PAYMENT_ORDER_ID = "uk_refunds_payment_order_id";

    private final PaymentOrderRepository paymentOrderRepository;
    private final RefundRepository refundRepository;
    private final WalletRepository walletRepository;
    private final PaymentGatewayClient paymentGatewayClient;
    private final WalletService walletService;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    // 외부 PortOne API 호출 중 DB 트랜잭션/락을 오래 잡지 않도록 메서드 전체 트랜잭션을 사용하지 않는다.
    // 필요한 DB 작업은 TransactionTemplate으로 짧게 나눠 실행한다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RefundResponse requestRefund(Long userId, String orderId, RefundRequest request) {
        // 환불 가능 여부를 먼저 짧은 트랜잭션에서 검증하고 PENDING 환불 이력을 생성한다.
        PreparedRefund preparation = transactionTemplate.execute(status ->
                prepareRefund(userId, orderId, request.reason()));

        // 이미 승인된 환불이면 PortOne을 다시 호출하지 않고 기존 환불 응답을 반환한다.
        if (preparation.alreadyApprovedRefund() != null) {
            return RefundResponse.from(preparation.alreadyApprovedRefund());
        }

        try {
            // PortOne 원결제 취소 호출은 DB/Wallet 락을 잡지 않은 상태에서 실행한다.
            paymentGatewayClient.cancelPayment(preparation.orderUid(), preparation.amount(), preparation.reason());
        } catch (RuntimeException e) {
            // cancelPayment 실패 시 PortOne이 실제로 취소 처리했는지 재확인한다.
            // 타임아웃 등으로 취소는 성공했으나 응답 수신이 실패한 경우:
            //   - 재시도 시 cancelPayment가 "이미 취소됨" 에러를 반환해 이 catch로 진입
            //   - verifyPayment로 CANCELLED 확인 후 완료 경로로 진행해 정산 불일치를 방지한다.
            try {
                PortOnePaymentInfo info = paymentGatewayClient.verifyPayment(preparation.orderUid());
                if (info.isCancelled()) {
                    return transactionTemplate.execute(status -> completeRefundAfterGatewayCancel(preparation));
                }
            } catch (RuntimeException ignored) {
                // PG 상태 조회도 실패 → 보수적으로 FAILED 처리
            }
            // PortOne 취소 실패나 예상치 못한 런타임 예외는 환불 이력만 FAILED로 남긴다.
            transactionTemplate.executeWithoutResult(status -> markRefundFailed(preparation.refundId()));
            throw new BusinessException(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE);
        }

        // PortOne 취소 성공 후 짧은 트랜잭션에서 주문 상태와 엽전 회수를 확정한다.
        return transactionTemplate.execute(status -> completeRefundAfterGatewayCancel(preparation));
    }

    private PreparedRefund prepareRefund(Long userId, String orderId, String reason) {
        // 결제 주문을 비관적 락으로 조회해 동시에 같은 주문 환불이 처리되지 않게 한다.
        PaymentOrder order = paymentOrderRepository.findByOrderUidWithLock(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_HISTORY_NOT_FOUND));

        // 요청 사용자가 결제 주문 소유자가 아니면 환불 요청을 거절한다.
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PAYMENT_HISTORY_FORBIDDEN);
        }

        // 기존 환불 이력이 있는지 확인해 중복 환불 요청을 방어한다.
        Refund existingRefund = refundRepository.findFirstByPaymentOrderIdOrderByIdDesc(order.getId()).orElse(null);
        // 주문이 이미 환불 완료 상태라면 APPROVED 환불 이력만 멱등 성공으로 반환한다.
        if (order.getStatus() == PaymentOrderStatus.REFUNDED) {
            if (existingRefund != null && existingRefund.getStatus() == RefundStatus.APPROVED) {
                return PreparedRefund.alreadyApproved(existingRefund);
            }
            throw new BusinessException(ErrorCode.DUPLICATE_REFUND_REQUEST);
        }

        // 결제 완료 주문만 사용자 환불 대상이다.
        if (order.getStatus() != PaymentOrderStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.REFUND_NOT_ELIGIBLE);
        }

        // 충전일 기준 환불 가능 기간이 지났으면 환불을 거절한다.
        if (order.getCreatedAt().isBefore(LocalDateTime.now(clock).minusDays(REFUND_PERIOD_DAYS))) {
            throw new BusinessException(ErrorCode.REFUND_PERIOD_EXPIRED);
        }

        // PortOne 호출 전 1차 잔액 검증만 수행한다. 실제 차감은 PG 성공 후 락을 잡고 다시 처리한다.
        Wallet wallet = walletRepository.findByUserId(order.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));
        if (wallet.getBalance() < order.getAmount()) {
            throw new BusinessException(ErrorCode.REFUND_BALANCE_INSUFFICIENT);
        }

        // 기존 환불이 이미 승인된 상태면 같은 결과를 반환하고 PortOne 중복 취소를 막는다.
        if (existingRefund != null && existingRefund.getStatus() == RefundStatus.APPROVED) {
            return PreparedRefund.alreadyApproved(existingRefund);
        }
        // 기존 환불이 대기 중이면 중복 요청으로 판단한다.
        if (existingRefund != null && existingRefund.getStatus() == RefundStatus.PENDING) {
            throw new BusinessException(ErrorCode.DUPLICATE_REFUND_REQUEST);
        }

        try {
            // PortOne 호출 전에 PENDING 환불 이력을 먼저 생성해 실패/재시도 상태를 추적할 수 있게 한다.
            Refund refund = Refund.create(order.getId(), userId, order.getAmount(), reason);
            refundRepository.saveAndFlush(refund);
            return PreparedRefund.pending(order.getOrderUid(), order.getAmount(), refund.getId(), reason);
        } catch (DataIntegrityViolationException e) {
            // 동일 주문 환불 동시 요청으로 유니크 제약이 터지면 중복 환불 요청으로 변환한다.
            Throwable cause = e;
            while (cause != null) {
                if (cause instanceof ConstraintViolationException cve
                        && UK_REFUNDS_PAYMENT_ORDER_ID.equalsIgnoreCase(cve.getConstraintName())) {
                    throw new BusinessException(ErrorCode.DUPLICATE_REFUND_REQUEST);
                }
                cause = cause.getCause();
            }
            throw e;
        }
    }

    private void markRefundFailed(Long refundId) {
        // PortOne 취소 실패 시 아직 대기 중인 환불만 FAILED로 전환한다.
        refundRepository.findByIdWithLock(refundId)
                .ifPresent(refund -> {
                    if (refund.getStatus() == RefundStatus.PENDING) {
                        refund.fail("PORTONE_CANCEL_FAILED");
                    }
                });
    }

    private RefundResponse completeRefundAfterGatewayCancel(PreparedRefund preparation) {
        // PortOne 취소 성공 후 주문을 다시 잠금 조회해 최신 상태 기준으로 확정 처리한다.
        PaymentOrder order = paymentOrderRepository.findByOrderUidWithLock(preparation.orderUid())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_HISTORY_NOT_FOUND));
        // 환불 이력도 잠금 조회해 중복 확정 처리를 방지한다.
        Refund refund = refundRepository.findByIdWithLock(preparation.refundId())
                .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_NOT_FOUND));

        // 환불이 이미 승인 상태라면 기존 승인 응답을 반환한다.
        if (refund.getStatus() == RefundStatus.APPROVED) {
            return RefundResponse.from(refund);
        }
        // PENDING이 아닌 환불은 현재 확정 처리할 수 없는 상태다.
        if (refund.getStatus() != RefundStatus.PENDING) {
            throw new BusinessException(ErrorCode.REFUND_INVALID_STATUS_TRANSITION);
        }

        // 아직 앱 DB에서 회수 가능한 주문 상태라면 가능한 엽전을 회수한다.
        if (order.getStatus() == PaymentOrderStatus.COMPLETED
                || order.getStatus() == PaymentOrderStatus.PARTIAL_CANCELLED) {
            long reclaimed = walletService.reclaimAvailableForRefund(
                    order.getUserId(),
                    order.getAmount(),
                    order.getId()
            );
            // PARTIAL_CANCELLED: PG 부분취소 이후 관리자 환불이 겹친 동시성 경합 케이스.
            // order.refund()는 COMPLETED만 허용하므로 throw → PG 취소는 이미 성공, reclaim은 롤백 → 정산 불일치.
            // 금액 복잡성(PG 부분취소분 중복 가능)으로 관리자 확인 상태로 남긴다.
            if (reclaimed == order.getAmount() && order.getStatus() == PaymentOrderStatus.COMPLETED) {
                order.refund();
            } else {
                order.requireAdjustment();
            }
        }

        // PortOne 취소는 성공했으므로 환불 이력은 승인 완료로 확정한다.
        refund.approve();
        return RefundResponse.from(refund);
    }

    public CursorPageResponse<UserRefundResponse> getUserRefundHistory(
            Long userId, RefundStatus status, String cursor, int size) {
        PageRequest pageable = PageRequest.of(0, size + 1);
        Long cursorId = CursorUtils.decodeSafe(cursor);
        List<Refund> refunds = refundRepository.findByUserIdWithFilter(userId, status, cursorId, pageable);

        boolean hasNext = refunds.size() > size;
        List<Refund> content = hasNext ? refunds.subList(0, size) : refunds;
        String nextCursor = hasNext ? CursorUtils.encode(content.get(content.size() - 1).getId()) : null;

        List<UserRefundResponse> responses = content.stream()
                .map(UserRefundResponse::from)
                .toList();

        return new CursorPageResponse<>(responses, nextCursor, hasNext, responses.size());
    }

    @Transactional
    public void cancelRefund(Long userId, Long refundId) {
        // 환불 요청을 잠금 조회해 관리자 승인과 사용자 취소가 동시에 처리되지 않게 한다.
        Refund refund = refundRepository.findByIdWithLock(refundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_NOT_FOUND));

        // 본인 환불 요청이 아니면 취소할 수 없다.
        if (!refund.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PAYMENT_HISTORY_FORBIDDEN);
        }

        // PENDING 환불 요청만 취소 상태로 전환한다.
        refund.cancel();
    }

    // 환불 준비 단계에서 만든 서비스 내부 전달 객체다.
    // PortOne 호출에 필요한 주문 정보와, 이미 승인된 환불이면 기존 Refund 엔티티를 함께 담는다.
    private record PreparedRefund(
            String orderUid,
            Long amount,
            Long refundId,
            String reason,
            Refund alreadyApprovedRefund
    ) {
        private static PreparedRefund pending(String orderUid, Long amount, Long refundId, String reason) {
            // PortOne 취소 호출에 필요한 주문 식별자와 환불 이력 id를 담아 반환한다.
            return new PreparedRefund(orderUid, amount, refundId, reason, null);
        }

        private static PreparedRefund alreadyApproved(Refund refund) {
            // 이미 승인된 환불은 외부 PG 호출 없이 반환할 기존 환불 이력만 담는다.
            return new PreparedRefund(null, null, null, null, refund);
        }
    }
}
