package com.chunbaetour.domain.payment.service;

import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.payment.exception.PaymentException;
import com.chunbaetour.domain.payment.client.PaymentGatewayClient;
import com.chunbaetour.domain.payment.dto.request.ChargeRequest;
import com.chunbaetour.domain.payment.dto.response.ChargeResponse;
import com.chunbaetour.domain.payment.entity.PaymentOrder;
import com.chunbaetour.domain.payment.repository.PaymentOrderRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChargeService {

    private static final long MIN_AMOUNT = 5_000L;
    private static final long MAX_AMOUNT = 100_000L;
    private static final long UNIT_AMOUNT = 1_000L;

    private final IdempotencyService idempotencyService;
    private final PaymentGatewayClient paymentGatewayClient;
    private final PaymentOrderRepository paymentOrderRepository;

    // 충전 플로우 요약:
    // [1] 금액 검증 → [2] 멱등성 키 점유 → [3] 포트원 사전등록 → [4] 주문 PENDING 저장 → [5] orderUid 반환
    // → 프론트: orderUid로 포트원 SDK 결제창 오픈 → 사용자 결제
    // → [추후 추가 - STORY-04] 포트원 콜백 수신 → 결제 검증 → PENDING → COMPLETED/FAILED 상태 전환
    // → [추후 추가 - STORY-04] COMPLETED 시 WalletService에 엽전 충전 메서드 호출
    // → [추후 추가 - STORY-04] FAILED/취소 시 멱등성 키 해제(unmark) → 사용자 재시도 가능
    @Transactional
    public ChargeResponse charge(Long userId, String idempotencyKey, ChargeRequest request) {
        // [1] 금액 2차 검증 (최소 5,000원 / 1,000원 단위 / 최대 100,000원)
        validateAmount(request.amount());

        // [2] 멱등성 키 점유 - 중복 요청 차단 (Redis 24시간 TTL)
        idempotencyService.checkAndMark(idempotencyKey);
        try {
            // [3] 포트원 사전등록 - 프론트가 결제창 열기 전 서버가 금액을 포트원에 등록 (위변조 방지)
            String orderUid = UUID.randomUUID().toString();
            paymentGatewayClient.preRegister(orderUid, request.amount());

            // [4] 주문 PENDING 저장 - 아직 실제 결제 완료 아님, 콜백 수신 후 상태 전환
            // TODO: 콜백 핸들러(PaymentCallbackController) 구현 필요 — STORY-04
            //       - POST /payments/callback/success : 결제 성공 콜백
            //         pgTransactionId로 포트원 결제 조회 → 금액 검증 → COMPLETED 전환
            //         → WalletService에 엽전 충전 메서드 호출 (STORY-04에서 구현)
            //       - POST /payments/callback/fail    : 결제 실패 콜백
            //         → FAILED 전환 + idempotencyService.unmark(idempotencyKey) 호출
            paymentOrderRepository.save(
                    PaymentOrder.create(orderUid, userId, request.amount(),
                            idempotencyKey, request.paymentMethod(), orderUid)
            );
            return new ChargeResponse(orderUid);
        } catch (RuntimeException ex) {
            // preRegister 또는 save 실패 시 키 해제 → 사용자 재시도 가능
            idempotencyService.unmark(idempotencyKey);
            throw ex;
        }
    }

    private void validateAmount(Long amount) {
        if (amount == null || amount < MIN_AMOUNT) {
            throw new PaymentException(ErrorCode.CHARGE_AMOUNT_TOO_LOW);
        }
        if (amount % UNIT_AMOUNT != 0) {
            throw new PaymentException(ErrorCode.INVALID_CHARGE_UNIT);
        }
        if (amount > MAX_AMOUNT) {
            throw new PaymentException(ErrorCode.CHARGE_AMOUNT_EXCEEDED);
        }
    }
}
