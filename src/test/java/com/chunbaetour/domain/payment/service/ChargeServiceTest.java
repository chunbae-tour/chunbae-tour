package com.chunbaetour.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.payment.client.PaymentGatewayClient;
import com.chunbaetour.domain.payment.config.PortOneProperties;
import com.chunbaetour.domain.payment.dto.request.ChargeRequest;
import com.chunbaetour.domain.payment.dto.response.ChargeResponse;
import com.chunbaetour.domain.payment.dto.response.PaymentHistoryResponse;
import com.chunbaetour.domain.payment.entity.PaymentOrder;
import com.chunbaetour.domain.payment.exception.PaymentException;
import com.chunbaetour.domain.payment.repository.PaymentOrderRepository;
import com.chunbaetour.domain.payment.type.PaymentMethod;
import com.chunbaetour.domain.payment.type.PaymentOrderStatus;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ChargeServiceTest {

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private PaymentGatewayClient paymentGatewayClient;

    @Mock
    private PaymentOrderRepository paymentOrderRepository;

    @Mock
    private PortOneProperties portOneProperties;

    @Mock
    private DailyChargeLimiter dailyChargeLimiter;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock chargeLock;

    @InjectMocks
    private ChargeService chargeService;

    @BeforeEach
    void setUpChargeLock() throws InterruptedException {
        // 충전 직렬화 분산락 — 금액 검증 이후 모든 경로가 락을 거치므로 lenient로 통과 stub.
        // 단위 테스트는 트랜잭션 밖이라 charge()가 finally 즉시해제 분기를 탄다.
        lenient().when(redissonClient.getLock(anyString())).thenReturn(chargeLock);
        lenient().when(chargeLock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);
        // unlockIfHeld는 isHeldByCurrentThread()가 true일 때만 unlock — 미스텁 시 기본 false라
        // finally 해제 분기가 한 번도 실행되지 않아 락 해제 계약이 검증 공백으로 남는다.
        lenient().when(chargeLock.isHeldByCurrentThread()).thenReturn(true);
    }

    @Test
    @DisplayName("정상 충전 요청 시 프론트 결제창 호출에 필요한 값을 반환한다")
    void charge_success_returns_payment_window_parameters() {
        willDoNothing().given(paymentGatewayClient).preRegister(anyString(), anyLong());
        willDoNothing().given(idempotencyService).checkAndMark(anyString());
        given(paymentOrderRepository.save(any(PaymentOrder.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(portOneProperties.getStoreId()).willReturn("store-id");
        given(portOneProperties.getChannel()).willReturn(Map.of(
                "card", "channel-card",
                "kakao-pay", "channel-kakao-pay",
                "toss-pay", "channel-toss-pay",
                "foreign-card", "channel-foreign-card"
        ));

        ChargeResponse response = chargeService.charge(1L, "idem-key-1", new ChargeRequest(10_000L, PaymentMethod.CARD));

        assertThat(response.orderUid()).isNotNull();
        assertThat(response.paymentId()).isEqualTo(response.orderUid());
        assertThat(response.storeId()).isEqualTo("store-id");
        assertThat(response.channelKey()).isEqualTo("channel-card");
        assertThat(response.orderName()).isEqualTo("춘배투어 엽전 10000원 충전");
        assertThat(response.totalAmount()).isEqualTo(10_000L);
        assertThat(response.currency()).isEqualTo("CURRENCY_KRW");
        assertThat(response.payMethod()).isEqualTo("CARD");
        verify(paymentOrderRepository).save(any(PaymentOrder.class));
        verify(paymentGatewayClient).preRegister(anyString(), anyLong());
        verify(idempotencyService, never()).unmark(anyString());
        verify(chargeLock).unlock(); // 정상 경로에서 락 해제 계약 핀 (트랜잭션 밖 finally 분기)
    }

    @Test
    @DisplayName("storeId 미설정(null) 시 멱등성 키 점유 전에 PAYMENT_SERVICE_UNAVAILABLE을 던진다")
    void charge_storeId_null_throws_before_idempotency_mark() {
        given(portOneProperties.getStoreId()).willReturn(null);

        assertThatThrownBy(() -> chargeService.charge(1L, "key", new ChargeRequest(10_000L, PaymentMethod.CARD)))
                .isInstanceOf(PaymentException.class)
                .extracting(ex -> ((PaymentException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE);

        verify(idempotencyService, never()).checkAndMark(anyString());
    }

    @Test
    @DisplayName("채널키 미설정(맵 없음) 시 멱등성 키 점유 전에 PAYMENT_SERVICE_UNAVAILABLE을 던진다")
    void charge_channelKey_missing_throws_before_idempotency_mark() {
        given(portOneProperties.getStoreId()).willReturn("store-id");
        given(portOneProperties.getChannel()).willReturn(null);

        assertThatThrownBy(() -> chargeService.charge(1L, "key", new ChargeRequest(10_000L, PaymentMethod.CARD)))
                .isInstanceOf(PaymentException.class)
                .extracting(ex -> ((PaymentException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE);

        verify(idempotencyService, never()).checkAndMark(anyString());
    }

    @Test
    @DisplayName("동일 멱등성 키로 재요청 시 PAY_007(중복 결제)을 던진다")
    void charge_duplicate_idempotency_throws_PAY_007() {
        // storeId/channelKey 검증이 멱등성 체크보다 먼저 실행되므로 선행 조건 스텁 필요
        given(portOneProperties.getStoreId()).willReturn("store-id");
        given(portOneProperties.getChannel()).willReturn(Map.of("card", "channel-card"));
        willThrow(new PaymentException(ErrorCode.DUPLICATE_PAYMENT_REQUEST))
                .given(idempotencyService).checkAndMark("dup-key");

        assertThatThrownBy(() -> chargeService.charge(1L, "dup-key", new ChargeRequest(10_000L, PaymentMethod.CARD)))
                .isInstanceOf(PaymentException.class)
                .extracting(ex -> ((PaymentException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_PAYMENT_REQUEST);
    }

    @Test
    @DisplayName("PG 사전등록 실패 시 멱등성 키를 해제하고 예외를 던진다")
    void charge_preRegister_failure_unmarks_idempotency_key() {
        given(portOneProperties.getStoreId()).willReturn("store-id");
        given(portOneProperties.getChannel()).willReturn(Map.of("card", "channel-card"));
        willDoNothing().given(idempotencyService).checkAndMark(anyString());
        willThrow(new PaymentException(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE))
                .given(paymentGatewayClient).preRegister(anyString(), anyLong());

        assertThatThrownBy(() -> chargeService.charge(1L, "key", new ChargeRequest(10_000L, PaymentMethod.CARD)))
                .isInstanceOf(PaymentException.class)
                .extracting(ex -> ((PaymentException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE);

        verify(idempotencyService).unmark("key");
        verify(chargeLock).unlock(); // 예외 경로(preRegister 실패)에서도 락 해제 보장
    }

    @Test
    @DisplayName("DB 저장 실패 시 멱등성 키를 해제하고 예외를 던진다")
    void charge_save_failure_unmarks_idempotency_key() {
        given(portOneProperties.getStoreId()).willReturn("store-id");
        given(portOneProperties.getChannel()).willReturn(Map.of("card", "channel-card"));
        willDoNothing().given(idempotencyService).checkAndMark(anyString());
        willDoNothing().given(paymentGatewayClient).preRegister(anyString(), anyLong());
        willThrow(new RuntimeException("DB error"))
                .given(paymentOrderRepository).save(any(PaymentOrder.class));

        assertThatThrownBy(() -> chargeService.charge(1L, "key", new ChargeRequest(10_000L, PaymentMethod.CARD)))
                .isInstanceOf(RuntimeException.class);

        verify(idempotencyService).unmark("key");
    }

    @Test
    @DisplayName("COMPLETED 주문의 멱등성 키 재사용 → PAY_007 (Redis TTL 만료 후 DB UNIQUE 위반 500 방지)")
    void charge_completedOrderSameIdempotencyKey_throwsPAY_007() {
        given(portOneProperties.getStoreId()).willReturn("store-id");
        given(portOneProperties.getChannel()).willReturn(Map.of("card", "channel-card"));
        // Redis TTL 만료 후 동일 키로 재요청 — COMPLETED 주문이 DB에 존재
        PaymentOrder completedOrder = PaymentOrder.create(
                "order-uid", 1L, 10_000L, "used-key", PaymentMethod.CARD, "pg-order");
        ReflectionTestUtils.setField(completedOrder, "status", PaymentOrderStatus.COMPLETED);
        given(paymentOrderRepository.findByIdempotencyKey("used-key")).willReturn(Optional.of(completedOrder));

        assertThatThrownBy(() -> chargeService.charge(1L, "used-key", new ChargeRequest(10_000L, PaymentMethod.CARD)))
                .isInstanceOf(PaymentException.class)
                .extracting(ex -> ((PaymentException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_PAYMENT_REQUEST);

        verify(idempotencyService, never()).checkAndMark(anyString());
        verify(paymentOrderRepository, never()).save(any(PaymentOrder.class));
    }

    @Test
    @DisplayName("PENDING 주문 멱등성 키 — 기존 주문 재활용 (재시도 응답)")
    void charge_pendingOrderSameIdempotencyKey_returnsExistingOrder() {
        given(portOneProperties.getStoreId()).willReturn("store-id");
        given(portOneProperties.getChannel()).willReturn(Map.of("card", "channel-card",
                "kakao-pay", "channel-kakao", "toss-pay", "channel-toss", "foreign-card", "channel-foreign"));
        PaymentOrder pendingOrder = PaymentOrder.create(
                "existing-uid", 1L, 10_000L, "retry-key", PaymentMethod.CARD, "pg-order");
        given(paymentOrderRepository.findByIdempotencyKey("retry-key")).willReturn(Optional.of(pendingOrder));

        ChargeResponse response = chargeService.charge(1L, "retry-key", new ChargeRequest(10_000L, PaymentMethod.CARD));

        assertThat(response.orderUid()).isEqualTo("existing-uid");
        verify(idempotencyService, never()).checkAndMark(anyString());
        verify(paymentOrderRepository, never()).save(any(PaymentOrder.class));
    }

    @Test
    @DisplayName("충전 금액이 5,000원 미만이면 PAY_002를 던진다")
    void charge_amount_too_low_throws_PAY_002() {
        assertThatThrownBy(() -> chargeService.charge(1L, "key", new ChargeRequest(4_000L, PaymentMethod.CARD)))
                .isInstanceOf(PaymentException.class)
                .extracting(ex -> ((PaymentException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CHARGE_AMOUNT_TOO_LOW);
    }

    @Test
    @DisplayName("충전 금액이 1,000원 단위가 아니면 PAY_003을 던진다")
    void charge_invalid_unit_throws_PAY_003() {
        assertThatThrownBy(() -> chargeService.charge(1L, "key", new ChargeRequest(6_500L, PaymentMethod.CARD)))
                .isInstanceOf(PaymentException.class)
                .extracting(ex -> ((PaymentException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CHARGE_UNIT);
    }

    @Test
    @DisplayName("충전 금액이 100,000원 초과이면 PAY_004를 던진다")
    void charge_amount_exceeded_throws_PAY_004() {
        assertThatThrownBy(() -> chargeService.charge(1L, "key", new ChargeRequest(200_000L, PaymentMethod.CARD)))
                .isInstanceOf(PaymentException.class)
                .extracting(ex -> ((PaymentException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CHARGE_AMOUNT_EXCEEDED);
    }

    @Test
    @DisplayName("신규 요청에서 일일 한도 초과 시 멱등성 키 점유 전에 PAY_030을 던진다 (KAN-293)")
    void charge_dailyLimitExceeded_throws_PAY_030_before_idempotency_mark() {
        given(portOneProperties.getStoreId()).willReturn("store-id");
        given(portOneProperties.getChannel()).willReturn(Map.of("card", "channel-card"));
        // 기존 주문 없음 — 신규 요청 경로로 진입해 한도 검증에 도달
        given(paymentOrderRepository.findByIdempotencyKey("key")).willReturn(Optional.empty());
        willThrow(new PaymentException(ErrorCode.DAILY_CHARGE_LIMIT_EXCEEDED))
                .given(dailyChargeLimiter).assertWithinDailyLimit(1L, 100_000L);

        assertThatThrownBy(() -> chargeService.charge(1L, "key", new ChargeRequest(100_000L, PaymentMethod.CARD)))
                .isInstanceOf(PaymentException.class)
                .extracting(ex -> ((PaymentException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DAILY_CHARGE_LIMIT_EXCEEDED);

        // 한도 초과는 외부 호출·키 점유 전에 차단 — 자원 미소모
        verify(idempotencyService, never()).checkAndMark(anyString());
        verify(paymentGatewayClient, never()).preRegister(anyString(), anyLong());
    }

    @Test
    @DisplayName("기존 PENDING 주문 재시도는 일일 한도 검증을 건너뛰고 멱등 재생한다 (KAN-293 멱등성 보존)")
    void charge_existingPendingOrder_skipsDailyLimitCheck() {
        given(portOneProperties.getStoreId()).willReturn("store-id");
        given(portOneProperties.getChannel()).willReturn(Map.of("card", "channel-card"));
        PaymentOrder pendingOrder = PaymentOrder.create(
                "existing-uid", 1L, 100_000L, "retry-key", PaymentMethod.CARD, "pg-order");
        given(paymentOrderRepository.findByIdempotencyKey("retry-key")).willReturn(Optional.of(pendingOrder));

        ChargeResponse response = chargeService.charge(1L, "retry-key", new ChargeRequest(100_000L, PaymentMethod.CARD));

        assertThat(response.orderUid()).isEqualTo("existing-uid");
        // 이미 만들어진 주문의 멱등 응답은 이후 누적액과 무관해야 함 — 한도 검증 미호출
        verify(dailyChargeLimiter, never()).assertWithinDailyLimit(anyLong(), anyLong());
    }

    @Test
    @DisplayName("충전 락 획득 실패(tryLock=false) 시 PAY_008(PAYMENT_PROCESSING)을 던지고 키 점유·PG 호출을 하지 않는다")
    void charge_lockNotAcquired_throws_PAY_008() throws InterruptedException {
        // 대기 시간 내 락을 못 잡음 — 같은 사용자의 충전이 임계구역 점유 중
        given(chargeLock.tryLock(anyLong(), any(TimeUnit.class))).willReturn(false);

        assertThatThrownBy(() -> chargeService.charge(1L, "key", new ChargeRequest(10_000L, PaymentMethod.CARD)))
                .isInstanceOf(PaymentException.class)
                .extracting(ex -> ((PaymentException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_PROCESSING);

        // 락 실패는 임계구역 진입 전 차단 — 자원 미소모, 잡지 못한 락은 해제도 하지 않음
        verify(idempotencyService, never()).checkAndMark(anyString());
        verify(paymentGatewayClient, never()).preRegister(anyString(), anyLong());
        verify(chargeLock, never()).unlock();
    }

    @Test
    @DisplayName("충전 락 대기 중 인터럽트 시 PAY_008을 던지고 인터럽트 상태를 복원한다")
    void charge_lockInterrupted_throws_PAY_008_and_restoresInterrupt() throws InterruptedException {
        given(chargeLock.tryLock(anyLong(), any(TimeUnit.class))).willThrow(new InterruptedException());

        try {
            assertThatThrownBy(() -> chargeService.charge(1L, "key", new ChargeRequest(10_000L, PaymentMethod.CARD)))
                    .isInstanceOf(PaymentException.class)
                    .extracting(ex -> ((PaymentException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_PROCESSING);

            // InterruptedException을 삼키지 않고 인터럽트 플래그를 복원한다 (호출 스레드 취소 신호 보존)
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(idempotencyService, never()).checkAndMark(anyString());
        } finally {
            // 테스트 스레드 인터럽트 플래그를 비워 후속 테스트로 누수 방지
            Thread.interrupted();
        }
    }

    // ── cancelCharge (KAN-252) ────────────────────────────────────────────────

    private PaymentOrder pendingOrder(Long userId) {
        return PaymentOrder.create("order-cancel", userId, 10_000L, "idem-cancel", PaymentMethod.CARD, "pg-cancel");
    }

    @Test
    @DisplayName("본인 PENDING 충전 주문 취소 성공 — CANCELLED 전환 + 멱등성 키 해제")
    void cancelCharge_pending_success() {
        PaymentOrder order = pendingOrder(1L);
        given(paymentOrderRepository.findByOrderUid("order-cancel")).willReturn(Optional.of(order));
        given(paymentOrderRepository.cancelIfPending("order-cancel")).willReturn(1);

        chargeService.cancelCharge(1L, "order-cancel");

        verify(paymentOrderRepository).cancelIfPending("order-cancel");
        verify(idempotencyService).unmark("idem-cancel"); // 재충전(새 키) 흐름 정상화
    }

    @Test
    @DisplayName("타인 충전 주문 취소 시 PAYMENT_HISTORY_NOT_FOUND (존재 숨김) — CAS·키해제 미수행")
    void cancelCharge_otherUser_notFound() {
        PaymentOrder order = pendingOrder(2L); // 주문 소유자 2L, 호출자 1L
        given(paymentOrderRepository.findByOrderUid("order-cancel")).willReturn(Optional.of(order));

        assertThatThrownBy(() -> chargeService.cancelCharge(1L, "order-cancel"))
                .isInstanceOf(PaymentException.class)
                .extracting(ex -> ((PaymentException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_HISTORY_NOT_FOUND);
        verify(paymentOrderRepository, never()).cancelIfPending(anyString());
        verify(idempotencyService, never()).unmark(anyString());
    }

    @Test
    @DisplayName("없는 충전 주문 취소 시 PAYMENT_HISTORY_NOT_FOUND")
    void cancelCharge_notFound() {
        given(paymentOrderRepository.findByOrderUid("nope")).willReturn(Optional.empty());

        assertThatThrownBy(() -> chargeService.cancelCharge(1L, "nope"))
                .isInstanceOf(PaymentException.class)
                .extracting(ex -> ((PaymentException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_HISTORY_NOT_FOUND);
    }

    @Test
    @DisplayName("완료/실패/취소된 주문 또는 웹훅 경합 — cancelIfPending=0 → PAYMENT_ORDER_NOT_CANCELLABLE, 키 미해제")
    void cancelCharge_notPending_throws() {
        // 소유권은 통과하지만 CAS UPDATE가 0 반환 — 이미 종착 상태이거나 웹훅이 먼저 COMPLETED/FAILED 전환
        PaymentOrder order = pendingOrder(1L);
        given(paymentOrderRepository.findByOrderUid("order-cancel")).willReturn(Optional.of(order));
        given(paymentOrderRepository.cancelIfPending("order-cancel")).willReturn(0);

        assertThatThrownBy(() -> chargeService.cancelCharge(1L, "order-cancel"))
                .isInstanceOf(PaymentException.class)
                .extracting(ex -> ((PaymentException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_ORDER_NOT_CANCELLABLE);
        verify(idempotencyService, never()).unmark(anyString());
    }

    // ── getPaymentHistory ────────────────────────────────────────────────────

    private PaymentOrder createOrder(Long id, Long amount) {
        PaymentOrder order = PaymentOrder.create(
                "order-" + id, 1L, amount, "idem-" + id, PaymentMethod.CARD, "pg-" + id);
        ReflectionTestUtils.setField(order, "id", id);
        return order;
    }

    @Test
    @DisplayName("결제 내역 조회 — cursor 없으면 첫 페이지 반환")
    void getPaymentHistory_noCursor_returnsFirstPage() {
        PaymentOrder o1 = createOrder(10L, 10_000L);
        PaymentOrder o2 = createOrder(9L, 5_000L);
        given(paymentOrderRepository.findByUserIdWithCursor(eq(1L), isNull(), any(Pageable.class)))
                .willReturn(List.of(o1, o2));

        CursorPageResponse<PaymentHistoryResponse> result = chargeService.getPaymentHistory(1L, null, 20);

        assertThat(result.content()).hasSize(2);
        assertThat(result.content().get(0).orderUid()).isEqualTo("order-10");
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
        // size 필드는 반환 개수(2)가 아니라 요청 size(20)를 echo한다 — 팀 표준 회귀 가드 (KAN-295 일관)
        assertThat(result.size()).isEqualTo(20);
    }

    @Test
    @DisplayName("결제 내역 조회 — size+1개 조회 시 hasNext=true, nextCursor 설정")
    void getPaymentHistory_hasNextPage_nextCursorSet() {
        // size=2, DB에서 3개(size+1) 반환 → hasNext=true
        PaymentOrder o1 = createOrder(10L, 10_000L);
        PaymentOrder o2 = createOrder(9L, 5_000L);
        PaymentOrder o3 = createOrder(8L, 3_000L);
        given(paymentOrderRepository.findByUserIdWithCursor(eq(1L), isNull(), any(Pageable.class)))
                .willReturn(List.of(o1, o2, o3));

        CursorPageResponse<PaymentHistoryResponse> result = chargeService.getPaymentHistory(1L, null, 2);

        assertThat(result.content()).hasSize(2);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isNotNull();
    }

    @Test
    @DisplayName("결제 내역 조회 — 결과 없으면 빈 목록")
    void getPaymentHistory_noResults_returnsEmpty() {
        given(paymentOrderRepository.findByUserIdWithCursor(eq(1L), isNull(), any(Pageable.class)))
                .willReturn(Collections.emptyList());

        CursorPageResponse<PaymentHistoryResponse> result = chargeService.getPaymentHistory(1L, null, 20);

        assertThat(result.content()).isEmpty();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }
}
