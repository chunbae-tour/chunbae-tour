package com.chunbaetour.domain.payment.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import com.chunbaetour.domain.payment.exception.PaymentException;
import com.chunbaetour.domain.payment.client.PaymentGatewayClient;
import com.chunbaetour.domain.payment.dto.request.ChargeRequest;
import com.chunbaetour.domain.payment.dto.response.ChargeResponse;
import com.chunbaetour.domain.payment.dto.response.PaymentHistoryResponse;
import com.chunbaetour.domain.payment.entity.PaymentOrder;
import com.chunbaetour.domain.payment.repository.PaymentOrderRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChargeService {

    private static final long MIN_AMOUNT = 5_000L;
    private static final long MAX_AMOUNT = 100_000L;
    private static final long UNIT_AMOUNT = 1_000L;

    private final IdempotencyService idempotencyService;
    private final PaymentGatewayClient paymentGatewayClient;
    private final PaymentOrderRepository paymentOrderRepository;

    // 충전 플로우:
    // [1] 금액 검증 → [2] 멱등성 키 점유 → [3] 포트원 사전등록 → [4] 주문 PENDING 저장 → [5] orderUid 반환
    // → 프론트: orderUid로 포트원 SDK 결제창 오픈 → 사용자 결제
    // → 포트원 웹훅(POST /payments/webhook) 수신 → CallbackService 검증 → COMPLETED/FAILED 전환
    // → COMPLETED: WalletService.charge()로 엽전 충전 / FAILED: idempotencyService.unmark()로 키 해제
    @Transactional
    public ChargeResponse charge(Long userId, String idempotencyKey, ChargeRequest request) {
        // [1] 금액 2차 검증 (최소 5,000원 / 1,000원 단위 / 최대 100,000원)
        validateAmount(request.amount());

        // [2] 멱등성 키 점유 — 중복 요청 차단 (Redis 24시간 TTL)
        idempotencyService.checkAndMark(idempotencyKey);
        try {
            // [3] 포트원 사전등록 — 프론트가 결제창 열기 전 서버가 금액을 등록 (위변조 방지)
            String orderUid = UUID.randomUUID().toString();
            paymentGatewayClient.preRegister(orderUid, request.amount());

            // [4] 주문 PENDING 저장 — 실제 결제 완료는 웹훅 수신 후 CallbackService에서 처리
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

    /**
     * 결제(충전) 내역 cursor 페이징 조회.
     * id DESC 기준. cursor 없으면 첫 페이지.
     *
     * <p>nextCursor 포맷: 마지막 항목의 id(Long)를 Base64URL 인코딩한 문자열 (padding 없음, URL-safe).
     * 클라이언트는 받은 nextCursor 값을 그대로 ?cursor= query param으로 전달하면 되며, 별도 파싱·변환 불필요.
     *
     * <p>status 노출 범위: PENDING·COMPLETED·FAILED·CANCELLED·REFUNDED 전체 상태를 포함한다.
     * FAILED·CANCELLED는 사용자가 충전 실패 원인을 확인할 수 있도록 의도적으로 노출.
     * 내부 처리 필터링이 필요하다면 API 버전 업 또는 쿼리 파라미터로 별도 제공.
     */
    public CursorPageResponse<PaymentHistoryResponse> getPaymentHistory(Long userId, String cursor, int size) {
        // 서비스 경계 방어 검증 — controller @Min/@Max 외 직접 호출 경로 보호
        if (userId == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Long cursorId = CursorUtils.decodeSafe(cursor);
        // size+1 조회 — 다음 페이지 존재 여부 판별
        List<PaymentOrder> orders = paymentOrderRepository.findByUserIdWithCursor(
                userId, cursorId, PageRequest.of(0, size + 1));

        boolean hasNext = orders.size() > size;
        List<PaymentOrder> content = hasNext ? orders.subList(0, size) : orders;
        String nextCursor = hasNext ? CursorUtils.encode(content.get(content.size() - 1).getId()) : null;

        List<PaymentHistoryResponse> responses = content.stream()
                .map(PaymentHistoryResponse::from)
                .toList();

        return new CursorPageResponse<>(responses, nextCursor, hasNext, responses.size());
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
