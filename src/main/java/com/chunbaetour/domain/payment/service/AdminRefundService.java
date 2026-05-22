package com.chunbaetour.domain.payment.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.payment.client.PaymentGatewayClient;
import com.chunbaetour.domain.payment.dto.response.RefundDetailResponse;
import com.chunbaetour.domain.payment.entity.PaymentOrder;
import com.chunbaetour.domain.payment.entity.Refund;
import com.chunbaetour.domain.payment.repository.PaymentOrderRepository;
import com.chunbaetour.domain.payment.repository.RefundRepository;
import com.chunbaetour.domain.payment.type.PaymentOrderStatus;
import com.chunbaetour.domain.payment.type.RefundStatus;
import com.chunbaetour.domain.yeopjeon.service.WalletService;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 환불 처리 서비스 (STORY-07).
 * 승인: 엽전 차감 → 상태 전이 → PG 취소 (PG 실패 시 @Transactional 롤백으로 DB 원복).
 * 거절: Refund 상태만 REJECTED로 변경.
 * 락 획득 순서: Refund → PaymentOrder → Wallet (데드락 방지).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminRefundService {

    private final RefundRepository refundRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentGatewayClient paymentGatewayClient;
    private final WalletService walletService;
    private final IdempotencyService idempotencyService;

    /**
     * 환불 승인.
     * DB 업데이트 후 PG 취소. PG 실패 시 @Transactional 롤백으로 DB 원복.
     */
    @Transactional
    public RefundDetailResponse approveRefund(Long refundId, String idempotencyKey) {
        idempotencyService.checkAndMark(idempotencyKey);
        try {
            // Refund 비관적 락 획득 (동시 승인/거절 방지)
            Refund refund = refundRepository.findByIdWithLock(refundId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_NOT_FOUND));

            // PENDING 상태만 처리 가능 (이미 승인/거절된 요청 방지)
            if (refund.getStatus() != RefundStatus.PENDING) {
                throw new BusinessException(ErrorCode.REFUND_INVALID_STATUS_TRANSITION);
            }

            // PaymentOrder 비관적 락 획득 (Refund 락 후 획득, 순서 일관성 유지)
            PaymentOrder order = paymentOrderRepository.findByIdWithLock(refund.getPaymentOrderId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_HISTORY_NOT_FOUND));

            // COMPLETED 상태만 환불 가능 (이미 취소된 주문 방지)
            if (order.getStatus() != PaymentOrderStatus.COMPLETED) {
                throw new BusinessException(ErrorCode.REFUND_NOT_ELIGIBLE);
            }

            // 유저 엽전 차감 + 환불 이력 저장 (잔액 부족 시 PAY_001)
            walletService.refund(refund.getUserId(), refund.getAmount(), refund.getPaymentOrderId());

            // 상태 전이
            refund.approve();
            order.cancel();

            // PG 환불 요청 — DB 작업 완료 후 마지막 호출 (PG 실패 시 @Transactional 롤백으로 DB 원복)
            paymentGatewayClient.cancelPayment(
                    order.getPgTransactionId(),
                    refund.getAmount(),
                    refund.getReason() != null ? refund.getReason() : "관리자 환불 승인"
            );

            return RefundDetailResponse.from(refund);
        } catch (RuntimeException ex) {
            idempotencyService.unmark(idempotencyKey);
            throw ex;
        }
    }

    /**
     * 환불 거절.
     * Refund 상태만 REJECTED로 변경. PG 호출 없음.
     */
    @Transactional
    public RefundDetailResponse rejectRefund(Long refundId, String idempotencyKey) {
        idempotencyService.checkAndMark(idempotencyKey);
        try {
            // Refund 비관적 락 획득
            Refund refund = refundRepository.findByIdWithLock(refundId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_NOT_FOUND));

            // PENDING 상태만 처리 가능
            if (refund.getStatus() != RefundStatus.PENDING) {
                throw new BusinessException(ErrorCode.REFUND_INVALID_STATUS_TRANSITION);
            }

            refund.reject();

            return RefundDetailResponse.from(refund);
        } catch (RuntimeException ex) {
            idempotencyService.unmark(idempotencyKey);
            throw ex;
        }
    }

    /**
     * 관리자 환불 목록 cursor 페이징 조회.
     * cursor 형식: {"id":N} → Base64URL 인코딩 (padding 없음).
     */
    public CursorPageResponse<RefundDetailResponse> getRefunds(String cursor, int size) {
        PageRequest pageable = PageRequest.of(0, size + 1);
        List<Refund> refunds = (cursor == null)
                ? refundRepository.findAllOrderByIdDesc(pageable)
                : refundRepository.findByIdLessThanOrderByIdDesc(decodeCursor(cursor), pageable);

        boolean hasNext = refunds.size() > size;
        List<Refund> content = hasNext ? refunds.subList(0, size) : refunds;
        String nextCursor = hasNext ? encodeCursor(content.get(content.size() - 1).getId()) : null;

        List<RefundDetailResponse> responses = content.stream()
                .map(RefundDetailResponse::from)
                .toList();

        return new CursorPageResponse<>(responses, nextCursor, hasNext, responses.size());
    }

    private String encodeCursor(Long id) {
        String json = "{\"id\":" + id + "}";
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private Long decodeCursor(String cursor) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor);
            String json = new String(decoded, StandardCharsets.UTF_8);
            String value = json.replaceAll(".*\"id\"\\s*:\\s*(\\d+).*", "$1");
            return Long.parseLong(value);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_CURSOR);
        }
    }
}
