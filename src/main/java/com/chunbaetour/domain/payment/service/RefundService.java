package com.chunbaetour.domain.payment.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import com.chunbaetour.domain.payment.client.PaymentGatewayClient;
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
        RefundPreparation preparation = transactionTemplate.execute(status ->
                prepareRefund(userId, orderId, request.reason()));

        // 이미 승인된 환불이면 PortOne을 다시 호출하지 않고 기존 환불 응답을 반환한다.
        if (preparation.alreadyApprovedResponse() != null) {
            return preparation.alreadyApprovedResponse();
        }

        try {
            // PortOne 원결제 취소 호출은 DB/Wallet 락을 잡지 않은 상태에서 실행한다.
            paymentGatewayClient.cancelPayment(preparation.orderUid(), preparation.amount(), preparation.reason());
        } catch (PaymentException e) {
            // PortOne 취소 실패 시 환불 이력만 FAILED로 남기고 주문/엽전 상태는 완료 처리하지 않는다.
            transactionTemplate.executeWithoutResult(status -> markRefundFailed(preparation.refundId()));
            throw new BusinessException(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE);
        }

        // PortOne 취소 성공 후 짧은 트랜잭션에서 주문 상태와 엽전 회수를 확정한다.
        return transactionTemplate.execute(status -> completeRefundAfterGatewayCancel(preparation));
    }

    private RefundPreparation prepareRefund(Long userId, String orderId, String reason) {
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
                return RefundPreparation.alreadyApproved(RefundResponse.from(existingRefund));
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
            return RefundPreparation.alreadyApproved(RefundResponse.from(existingRefund));
        }
        // 기존 환불이 대기 중이면 중복 요청으로 판단한다.
        if (existingRefund != null && existingRefund.getStatus() == RefundStatus.PENDING) {
            throw new BusinessException(ErrorCode.DUPLICATE_REFUND_REQUEST);
        }

        try {
            // PortOne 호출 전에 PENDING 환불 이력을 먼저 생성해 실패/재시도 상태를 추적할 수 있게 한다.
            Refund refund = Refund.create(order.getId(), userId, order.getAmount(), reason);
            refundRepository.saveAndFlush(refund);
            return RefundPreparation.pending(order.getOrderUid(), order.getAmount(), refund.getId(), reason);
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

    private RefundResponse completeRefundAfterGatewayCancel(RefundPreparation preparation) {
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
            // 전액 회수에 성공하면 주문을 REFUNDED로 확정한다.
            if (reclaimed == order.getAmount()) {
                order.refund();
            } else {
                // 잔액 부족으로 일부만 회수되면 관리자 확인 필요 상태로 남긴다.
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

    private record RefundPreparation(
            String orderUid,
            Long amount,
            Long refundId,
            String reason,
            RefundResponse alreadyApprovedResponse
    ) {
        private static RefundPreparation pending(String orderUid, Long amount, Long refundId, String reason) {
            // PortOne 취소 호출에 필요한 주문 식별자와 환불 이력 id를 담아 반환한다.
            return new RefundPreparation(orderUid, amount, refundId, reason, null);
        }

        private static RefundPreparation alreadyApproved(RefundResponse response) {
            // 이미 승인된 환불은 외부 PG 호출 없이 바로 반환할 응답만 담는다.
            return new RefundPreparation(null, null, null, null, response);
        }
    }
}
