package com.chunbaetour.domain.payment.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import com.chunbaetour.domain.payment.dto.request.RefundRequest;
import com.chunbaetour.domain.payment.dto.response.RefundResponse;
import com.chunbaetour.domain.payment.dto.response.UserRefundResponse;
import com.chunbaetour.domain.payment.entity.PaymentOrder;
import com.chunbaetour.domain.payment.entity.Refund;
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
    private static final String PARTIAL_REFUND_REJECT_REASON = "부분환불은 불가합니다";

    private final PaymentOrderRepository paymentOrderRepository;
    private final RefundRepository refundRepository;
    private final WalletRepository walletRepository;
    private final WalletService walletService;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    /**
     * 환불 요청 접수.
     *
     * <p>전액환불 정책: 잔액 == 충전액인 경우만 PENDING으로 접수, 스케줄러가 PG 취소 처리.
     * 잔액이 부족한 경우(부분 사용) 즉시 REJECTED 기록을 남기고 에러 반환.
     * PG 직접 호출 없이 DB 저장만 수행해 응답 속도와 장애 격리를 보장한다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RefundResponse requestRefund(Long userId, String orderId, RefundRequest request) {
        Refund refund = transactionTemplate.execute(status ->
                prepareRefundRecord(userId, orderId, request.reason()));

        // 부분환불 거절 — REJECTED 기록은 저장됨, 사용자에게 에러 반환
        if (refund.getStatus() == RefundStatus.REJECTED) {
            throw new BusinessException(ErrorCode.REFUND_BALANCE_INSUFFICIENT);
        }
        // PENDING 접수 완료 — 스케줄러가 처리
        return RefundResponse.from(refund);
    }

    private Refund prepareRefundRecord(Long userId, String orderId, String reason) {
        // 결제 주문 비관적 락 조회 — 동시 환불 요청 방지
        PaymentOrder order = paymentOrderRepository.findByOrderUidWithLock(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_HISTORY_NOT_FOUND));

        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PAYMENT_HISTORY_FORBIDDEN);
        }

        // 최근 환불 이력 확인
        Refund existingRefund = refundRepository.findFirstByPaymentOrderIdOrderByIdDesc(order.getId()).orElse(null);

        // 이미 환불 완료된 주문 — 멱등 응답
        if (order.getStatus() == PaymentOrderStatus.REFUNDED) {
            if (existingRefund != null && existingRefund.getStatus() == RefundStatus.APPROVED) {
                return existingRefund;
            }
            throw new BusinessException(ErrorCode.DUPLICATE_REFUND_REQUEST);
        }

        // COMPLETED 주문만 환불 가능
        if (order.getStatus() != PaymentOrderStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.REFUND_NOT_ELIGIBLE);
        }

        // 환불 가능 기간 검증
        if (order.getCreatedAt().isBefore(LocalDateTime.now(clock).minusDays(REFUND_PERIOD_DAYS))) {
            throw new BusinessException(ErrorCode.REFUND_PERIOD_EXPIRED);
        }

        // PENDING 중복 요청 방지
        if (existingRefund != null && existingRefund.getStatus() == RefundStatus.PENDING) {
            throw new BusinessException(ErrorCode.DUPLICATE_REFUND_REQUEST);
        }

        // 잔액 확인 — 전액 환불 정책
        Wallet wallet = walletRepository.findByUserId(order.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));

        try {
            Refund refund = Refund.create(order.getId(), userId, order.getAmount(), reason);

            if (wallet.getBalance() < order.getAmount()) {
                // 부분 사용 감지 → 즉시 REJECTED 저장, 이력 보존
                refund.reject(PARTIAL_REFUND_REJECT_REASON);
                refundRepository.save(refund);
                return refund; // REJECTED 상태
            }

            // 전액 잔액 — PENDING 접수
            refundRepository.saveAndFlush(refund);
            return refund; // PENDING 상태
        } catch (DataIntegrityViolationException e) {
            // 동시 요청 UK 위반 → 중복 요청으로 변환
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

    /**
     * 스케줄러 PG 취소 성공 후 환불 완료 처리.
     * PENDING(최초 처리) 또는 FAILED(재시도) 상태 환불을 APPROVED로 전환하고 엽전을 회수한다.
     */
    @Transactional
    public void completeSchedulerRetry(Long refundId, String orderUid, Long amount) {
        PaymentOrder order = paymentOrderRepository.findByOrderUidWithLock(orderUid)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_HISTORY_NOT_FOUND));
        Refund refund = refundRepository.findByIdWithLock(refundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_NOT_FOUND));

        if (refund.getStatus() == RefundStatus.APPROVED) {
            return; // 이미 처리됨 (멱등)
        }
        if (refund.getStatus() != RefundStatus.PENDING && refund.getStatus() != RefundStatus.FAILED) {
            return; // 처리 불가 상태
        }

        // PG 취소 완료 — 가능한 엽전 회수
        if (order.getStatus() == PaymentOrderStatus.COMPLETED
                || order.getStatus() == PaymentOrderStatus.PARTIAL_CANCELLED) {
            long reclaimed = walletService.reclaimAvailableForRefund(
                    order.getUserId(), amount, order.getId());
            // PARTIAL_CANCELLED: PG 부분취소와 환불이 겹친 동시성 케이스 — 관리자 확인
            if (reclaimed == amount && order.getStatus() == PaymentOrderStatus.COMPLETED) {
                order.refund();
            } else {
                order.requireAdjustment();
            }
        }
        refund.approveFromScheduler();
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
        Refund refund = refundRepository.findByIdWithLock(refundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_NOT_FOUND));

        if (!refund.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PAYMENT_HISTORY_FORBIDDEN);
        }

        refund.cancel();
    }
}
