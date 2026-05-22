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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 관리자 환불 처리 서비스 (STORY-07).
 * 승인: 엽전 차감 → 상태 전이 → DB 커밋 → afterCommit에서 PG 취소.
 *       PG 취소를 afterCommit으로 분리해 "PG 성공 후 DB 커밋 실패" 상태 불일치를 방지.
 * 거절: Refund 상태만 REJECTED로 변경.
 * 락 획득 순서: Refund → PaymentOrder → Wallet (데드락 방지).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminRefundService {

    private static final Pattern CURSOR_PATTERN = Pattern.compile("^\\{\"id\":(\\d+)\\}$");

    private final RefundRepository refundRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentGatewayClient paymentGatewayClient;
    private final WalletService walletService;

    /**
     * 환불 승인.
     * DB 업데이트 커밋 후 afterCommit에서 PG 취소.
     * PG 실패 시 DB 상태 유지(APPROVED) — 수동 재시도 또는 아웃박스 패턴으로 대응 필요.
     */
    @Transactional
    public RefundDetailResponse approveRefund(Long refundId) {
        // Refund 비관적 락 획득 (동시 승인/거절 방지)
        Refund refund = refundRepository.findByIdWithLock(refundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_NOT_FOUND));

        // PENDING 상태 사전 체크 — 실패 시 아래 PaymentOrder findByIdWithLock(DB 쿼리+락) 건너뜀
        // 엔티티 approve()도 동일 체크를 수행하지만, 불필요한 DB 접근 방지 목적으로 서비스 레이어에서도 유지
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

        // PG 취소는 DB 커밋 확정 후 실행 — "PG 성공 후 커밋 실패" 시 상태 불일치 방지
        // 트랜잭션 없는 컨텍스트(단위 테스트 등)에서는 즉시 실행
        String pgTxId = order.getPgTransactionId();
        Long refundAmount = refund.getAmount();
        String reason = refund.getReason() != null ? refund.getReason() : "관리자 환불 승인";
        schedulePgCancel(pgTxId, refundAmount, reason);

        return RefundDetailResponse.from(refund);
    }

    /**
     * 환불 거절.
     * Refund 상태만 REJECTED로 변경. PG 호출 없음.
     */
    @Transactional
    public RefundDetailResponse rejectRefund(Long refundId) {
        // Refund 비관적 락 획득
        Refund refund = refundRepository.findByIdWithLock(refundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_NOT_FOUND));

        // 상태 전이 — PENDING이 아니면 엔티티가 REFUND_INVALID_STATUS_TRANSITION(PAY_020) throw
        refund.reject();

        return RefundDetailResponse.from(refund);
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
            Matcher matcher = CURSOR_PATTERN.matcher(json);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("invalid cursor format");
            }
            return Long.parseLong(matcher.group(1));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_CURSOR);
        }
    }

    /** DB 커밋 확정 후 PG 취소 예약. 트랜잭션 없는 컨텍스트(단위 테스트 등)에서는 즉시 실행. */
    private void schedulePgCancel(String pgTxId, Long amount, String reason) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    paymentGatewayClient.cancelPayment(pgTxId, amount, reason);
                }
            });
        } else {
            paymentGatewayClient.cancelPayment(pgTxId, amount, reason);
        }
    }
}
