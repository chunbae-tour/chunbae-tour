package com.chunbaetour.domain.payment.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
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
import com.chunbaetour.domain.common.util.CursorUtils;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 환불 요청 서비스 (STORY-06, KAN-115).
 * 유저가 환불을 요청하면 Refund 엔티티를 PENDING으로 생성.
 * 실제 PG 환불은 STORY-07 관리자 승인 시점에 수행.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefundService {

    private static final int REFUND_PERIOD_DAYS = 7;
    private static final String UK_REFUNDS_PAYMENT_ORDER_ID = "uk_refunds_payment_order_id";

    private final PaymentOrderRepository paymentOrderRepository;
    private final RefundRepository refundRepository;
    private final WalletRepository walletRepository;

    /**
     * 환불 요청 생성.
     * orderId = PaymentOrder.orderUid (UUID 문자열, 충전 응답으로 전달받은 값).
     *
     * <p>검증 순서:
     * 1. 주문 존재 확인 → PAY_009
     * 2. 본인 주문 확인 → PAY_011
     * 3. COMPLETED 상태 확인 → PAY_015
     * 4. 환불 기간(7일) 확인 → PAY_010
     * 5. 잔액 전액 보유 확인 → PAY_017 (부분 사용 후 환불 불가)
     * 6. 중복 환불 요청 확인 → PAY_016
     *
     * <p><b>잔액 검증 주의:</b> 본 메서드는 요청 시점 잔액만 검증한다.
     * PENDING 상태에서 사용자가 엽전을 추가 소비하면 잔액이 줄 수 있으므로,
     * 관리자 승인(STORY-07) 시점에 잔액 재검증 + 차감을 원자적으로 수행해야 한다.
     */
    @Transactional
    public RefundResponse requestRefund(Long userId, String orderId, RefundRequest request) {
        // 주문 조회 (orderUid = 충전 시 발급된 UUID)
        PaymentOrder order = paymentOrderRepository.findByOrderUid(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_HISTORY_NOT_FOUND));

        // 본인 주문인지 확인
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PAYMENT_HISTORY_FORBIDDEN);
        }

        // COMPLETED 상태만 환불 가능 (PENDING/FAILED/CANCELLED 불가)
        if (order.getStatus() != PaymentOrderStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.REFUND_NOT_ELIGIBLE);
        }

        // 환불 기간(7일) 초과 확인
        if (order.getCreatedAt().isBefore(LocalDateTime.now().minusDays(REFUND_PERIOD_DAYS))) {
            throw new BusinessException(ErrorCode.REFUND_PERIOD_EXPIRED);
        }

        // 미사용 엽전 전액 보유 확인 (부분 사용 후 환불 불가, 요청 시점 1차 검증)
        // 관리자 승인 시점에 WalletService.refund()가 비관적 락 + 잔액 재검증 + 차감을 원자적으로 수행
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));
        if (wallet.getBalance() < order.getAmount()) {
            throw new BusinessException(ErrorCode.REFUND_BALANCE_INSUFFICIENT);
        }

        // 동일 주문에 대한 중복 환불 요청 방지
        if (refundRepository.existsByPaymentOrderIdAndStatus(order.getId(), RefundStatus.PENDING)) {
            throw new BusinessException(ErrorCode.DUPLICATE_REFUND_REQUEST);
        }

        // 환불 요청 생성 (전액 환불, PENDING 상태)
        // uk_refunds_payment_order_id 위반 시 동시 중복 요청 방어
        try {
            Refund refund = Refund.create(order.getId(), userId, order.getAmount(), request.reason());
            refundRepository.saveAndFlush(refund);
            return RefundResponse.from(refund);
        } catch (DataIntegrityViolationException e) {
            if (e.getCause() instanceof ConstraintViolationException cve
                    && UK_REFUNDS_PAYMENT_ORDER_ID.equalsIgnoreCase(cve.getConstraintName())) {
                throw new BusinessException(ErrorCode.DUPLICATE_REFUND_REQUEST);
            }
            throw e;
        }
    }

    /**
     * 사용자 환불 내역 cursor 페이징 조회 (KAN-115).
     * status 파라미터 생략 시 전체 상태 조회.
     * cursor 형식: {"id":N} → Base64URL 인코딩 (padding 없음).
     */
    public CursorPageResponse<UserRefundResponse> getUserRefundHistory(
            Long userId, RefundStatus status, String cursor, int size) {
        // 페이지 크기 검증
        if (size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.INVALID_PAGE_SIZE);
        }

        // size+1개 조회 → hasNext 판단 후 실제 size개만 응답
        PageRequest pageable = PageRequest.of(0, size + 1);
        Long cursorId = cursor != null ? decodeCursorSafe(cursor) : null;

        // status/cursorId null이면 조건 미적용 — 4가지 조합을 쿼리 1개로 처리
        List<Refund> refunds = refundRepository.findByUserIdWithFilter(userId, status, cursorId, pageable);

        // 다음 페이지 존재 여부 판단 + nextCursor 생성
        boolean hasNext = refunds.size() > size;
        List<Refund> content = hasNext ? refunds.subList(0, size) : refunds;
        String nextCursor = hasNext ? CursorUtils.encode(content.get(content.size() - 1).getId()) : null;

        List<UserRefundResponse> responses = content.stream()
                .map(UserRefundResponse::from)
                .toList();

        return new CursorPageResponse<>(responses, nextCursor, hasNext, responses.size());
    }

    /** 환불 요청 취소. PENDING 상태만 취소 가능 → PAY_019. 타인 요청 취소 시 → PAY_011. */
    @Transactional
    public void cancelRefund(Long userId, Long refundId) {
        // 비관적 락 획득 — 관리자 approve와 사용자 cancel 동시 실행 시 race condition 방지
        Refund refund = refundRepository.findByIdWithLock(refundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_NOT_FOUND));

        // 본인 환불 요청인지 확인
        if (!refund.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PAYMENT_HISTORY_FORBIDDEN);
        }

        // 상태 전이 — PENDING이 아니면 엔티티가 REFUND_CANCEL_NOT_ALLOWED(PAY_019) throw
        refund.cancel();
    }

    private Long decodeCursorSafe(String cursor) {
        try {
            return CursorUtils.decode(cursor);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_CURSOR);
        }
    }
}
