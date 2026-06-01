package com.chunbaetour.domain.payment.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import com.chunbaetour.domain.payment.client.PaymentGatewayClient;
import com.chunbaetour.domain.payment.config.PortOneProperties;
import com.chunbaetour.domain.payment.dto.request.ChargeRequest;
import com.chunbaetour.domain.payment.dto.response.ChargeResponse;
import com.chunbaetour.domain.payment.dto.response.PaymentHistoryResponse;
import com.chunbaetour.domain.payment.entity.PaymentOrder;
import com.chunbaetour.domain.payment.exception.PaymentException;
import com.chunbaetour.domain.payment.repository.PaymentOrderRepository;
import com.chunbaetour.domain.payment.type.PaymentMethod;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final PortOneProperties portOneProperties;

    // 충전 플로우:
    // [1] 금액 검증 → [1.5] PortOne 설정 검증 → [2] 멱등성 키 점유 → [3] 포트원 사전등록 → [4] 주문 PENDING 저장 → [5] orderUid 반환
    // → 프론트: orderUid로 포트원 SDK 결제창 오픈 → 사용자 결제
    // → 포트원 웹훅(POST /payments/webhook) 수신 → CallbackService 검증 → COMPLETED/FAILED 전환
    // → COMPLETED: WalletService.charge()로 엽전 충전 / FAILED: idempotencyService.unmark()로 키 해제
    @Transactional
    public ChargeResponse charge(Long userId, String idempotencyKey, ChargeRequest request) {
        // [1] 금액 2차 검증 (최소 5,000원 / 1,000원 단위 / 최대 100,000원)
        validateAmount(request.amount());

        // [1.5] PortOne 설정 누락 검증 — 키 점유 전에 실행해 불필요한 멱등성 키 소모 방지
        String storeId = portOneProperties.getStoreId();
        if (storeId == null || storeId.isBlank()) {
            throw new PaymentException(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE);
        }
        String channelKey = resolveChannelKey(request.paymentMethod());

        // [1.7] 기존 PENDING 주문 재활용 — 네트워크 실패 후 재시도 시 동일 ChargeResponse 반환
        // 정상 멱등성 동작: 같은 키로 응답을 못 받은 경우 기존 주문 정보로 동일한 응답 재생성
        Optional<PaymentOrder> existingOrder = paymentOrderRepository.findPendingByIdempotencyKey(idempotencyKey);
        if (existingOrder.isPresent()) {
            PaymentOrder order = existingOrder.get();
            // 기존 주문의 결제수단으로 채널키 재해석 — request.paymentMethod()와 다를 수 있으므로 일관성 보장
            return ChargeResponse.from(order.getOrderUid(), order.getAmount(),
                    order.getPaymentMethod(), storeId,
                    resolveChannelKey(order.getPaymentMethod()));
        }

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
            return ChargeResponse.from(
                orderUid,
                request.amount(),
                request.paymentMethod(),
                storeId,
                channelKey
            );
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
    // 결제수단에 대응하는 PortOne 채널키를 application.yml channel 맵에서 조회해 반환
    // 맵 미설정 또는 키 오타 시 즉시 예외로 원인 고정 — null이 결제창 필수값으로 내려가는 것 방지
    private String resolveChannelKey(PaymentMethod paymentMethod) {
        Map<String, String> channel = portOneProperties.getChannel();
        if (channel == null) {
            throw new PaymentException(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE);
        }
        String channelKey = channel.get(toChannelPropertyKey(paymentMethod));
        if (channelKey == null || channelKey.isBlank()) {
            throw new PaymentException(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE);
        }
        return channelKey;
    }

    // PaymentMethod enum을 application.yml channel 맵의 키 문자열로 변환 (예: KAKAO_PAY → "kakao-pay")
    private String toChannelPropertyKey(PaymentMethod paymentMethod) {
        return switch (paymentMethod) {
            case CARD -> "card";
            case KAKAO_PAY -> "kakao-pay";
            case TOSS_PAY -> "toss-pay";
            case FOREIGN_CARD -> "foreign-card";
        };
    }
}
