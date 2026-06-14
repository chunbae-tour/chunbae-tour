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
import com.chunbaetour.domain.payment.type.PaymentOrderStatus;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChargeService {

    private static final long MIN_AMOUNT = 5_000L;
    private static final long MAX_AMOUNT = 100_000L;
    private static final long UNIT_AMOUNT = 1_000L;
    /** 사용자 단위 충전 직렬화 락 키 — 동시 충전의 일일 한도 TOCTOU 차단 (KAN-293). */
    private static final String CHARGE_LOCK_KEY = "charge:lock:%d";
    // 락 대기 상한 — 임계구역 내 PortOne preRegister read 타임아웃(5s, PortOneConfig)보다 크게 잡아
    // 락 보유자가 PG 응답을 정상 대기하는 동안 같은 사용자의 후속 요청이 PAY_008(503)로 새지 않게 한다 (KAN-293 리뷰 M1).
    private static final int CHARGE_LOCK_WAIT_SECONDS = 7;

    private final IdempotencyService idempotencyService;
    private final PaymentGatewayClient paymentGatewayClient;
    private final PaymentOrderRepository paymentOrderRepository;
    private final PortOneProperties portOneProperties;
    private final DailyChargeLimiter dailyChargeLimiter;
    private final RedissonClient redissonClient;

    // 충전 플로우:
    // [1] 금액 검증 → [1.5] PortOne 설정 검증 → [2] 멱등성 키 점유 → [3] 포트원 사전등록 → [4] 주문 PENDING 저장 → [5] orderUid 반환
    // → 프론트: orderUid로 포트원 SDK 결제창 오픈 → 사용자 결제
    // → 포트원 웹훅(POST /payments/webhook) 수신 → CallbackService 검증 → COMPLETED/FAILED 전환
    // → COMPLETED: WalletService.charge()로 엽전 충전 / FAILED: idempotencyService.unmark()로 키 해제
    @Transactional
    public ChargeResponse charge(Long userId, String idempotencyKey, ChargeRequest request) {
        // [1] 금액 2차 검증 (최소 5,000원 / 1,000원 단위 / 최대 100,000원)
        validateAmount(request.amount());

        // [1.3] 사용자 단위 분산락 (KAN-293 리뷰 M2) — 일일 한도 SUM 조회 → PENDING INSERT 사이 TOCTOU 차단.
        // 서로 다른 멱등키로 동시에 들어온 같은 사용자 요청이 같은 누적 스냅샷을 읽고 모두 통과해 한도를
        // 초과(예: 40만에서 10만 2건 동시 → 60만)하는 것을 막는다. 다음 요청이 확정된 PENDING을 보도록
        // finally(커밋 직전)가 아니라 트랜잭션 완료 후(afterCompletion)에 해제한다.
        RLock lock = redissonClient.getLock(CHARGE_LOCK_KEY.formatted(userId));
        acquireChargeLock(lock);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    unlockIfHeld(lock);
                }
            });
            return chargeWithinLock(userId, idempotencyKey, request);
        }
        // 트랜잭션 컨텍스트 밖(직접 호출·테스트): 즉시 해제
        try {
            return chargeWithinLock(userId, idempotencyKey, request);
        } finally {
            unlockIfHeld(lock);
        }
    }

    private ChargeResponse chargeWithinLock(Long userId, String idempotencyKey, ChargeRequest request) {
        // [1.5] PortOne 설정 누락 검증 — 키 점유 전에 실행해 불필요한 멱등성 키 소모 방지
        String storeId = portOneProperties.getStoreId();
        if (storeId == null || storeId.isBlank()) {
            throw new PaymentException(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE);
        }
        String channelKey = resolveChannelKey(request.paymentMethod());

        // [1.7] 멱등성 키로 기존 주문 확인
        // - PENDING: 네트워크 실패 후 재시도 → 동일 응답 재생성 (정상 멱등성 동작)
        // - PENDING 외: 완료/실패된 주문에 동일 키 재사용 → DB UNIQUE 위반 대신 PAY_007 반환
        Optional<PaymentOrder> existingOrder = paymentOrderRepository.findByIdempotencyKey(idempotencyKey);
        if (existingOrder.isPresent()) {
            PaymentOrder order = existingOrder.get();
            if (order.getStatus() == PaymentOrderStatus.PENDING) {
                // 기존 주문의 결제수단으로 채널키 재해석 — request.paymentMethod()와 다를 수 있으므로 일관성 보장
                return ChargeResponse.from(order.getOrderUid(), order.getAmount(),
                        order.getPaymentMethod(), storeId,
                        resolveChannelKey(order.getPaymentMethod()));
            }
            // Redis TTL(24h) 만료 후 동일 키 재사용 시 DB UNIQUE 위반 → 500 방지
            throw new PaymentException(ErrorCode.DUPLICATE_PAYMENT_REQUEST);
        }

        // [1.8] 일일 충전 한도 검증 (KAN-293) — 신규 요청에만 적용.
        // payment_orders의 당일 충전 누적(진행중 PENDING + 완료 이력)을 SUM 조회해 한도 초과를 차단한다.
        // 기존 주문 재생/PAY_007 분기([1.7]) 이후에 둬, 이미 만들어진 주문의 멱등 응답이
        // 이후 누적액 변화로 PAY_030으로 뒤바뀌는 멱등성 계약 위반을 막는다.
        // 키 점유([2])·외부 호출([3]) 전이라 한도 초과 요청은 자원을 소모하지 않는다.
        dailyChargeLimiter.assertWithinDailyLimit(userId, request.amount());

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
     * 충전 주문 사용자 직접 취소 (KAN-252, USER 전용).
     *
     * <p>본인 소유 PENDING 충전 주문만 취소 가능. 결제창 완료 전 대기 상태를 해제하고 즉시 재시도할 수 있게 한다.
     * 웹훅(COMPLETED/FAILED 전환)과 경합해도 {@code cancelIfPending} DB-level CAS로 직렬화 —
     * 한쪽만 전환 성공, 0 반환 시 이미 종착 상태로 보고 PAYMENT_ORDER_NOT_CANCELLABLE.
     *
     * <p>멱등성 키는 취소 시 해제한다. DB의 idempotencyKey UNIQUE 제약은 영구이므로 <b>동일 키</b> 재충전은
     * 여전히 PAY_007로 막히지만(의도 — 멱등성 유지), 프론트가 새 키로 재시도하는 흐름은 정상 동작한다.
     * 키 해제는 <b>커밋 이후</b>로 미룬다 — 트랜잭션 롤백 시 주문이 PENDING으로 복구되는데 Redis 키만 먼저
     * 삭제되면 멱등 가드가 풀려 중복 주문 창이 열리므로(CallbackService.scheduleUnmark와 동일 정책).
     *
     * @param orderId 충전 시 발급된 orderUid(UUID)
     * @throws PaymentException PAYMENT_HISTORY_NOT_FOUND(타인/없음), PAYMENT_ORDER_NOT_CANCELLABLE(PENDING 아님)
     */
    @Transactional
    public void cancelCharge(Long userId, String orderId) {
        // 소유권 확인 — 타인/없는 주문은 존재를 숨겨 NOT_FOUND로 통일
        PaymentOrder order = paymentOrderRepository.findByOrderUid(orderId)
                .orElseThrow(() -> new PaymentException(ErrorCode.PAYMENT_HISTORY_NOT_FOUND));
        if (!order.getUserId().equals(userId)) {
            throw new PaymentException(ErrorCode.PAYMENT_HISTORY_NOT_FOUND);
        }

        // PENDING → CANCELLED 조건부 CAS — 웹훅/경합 방어. 0이면 이미 완료/실패/취소된 주문
        int cancelled = paymentOrderRepository.cancelIfPending(orderId);
        if (cancelled == 0) {
            throw new PaymentException(ErrorCode.PAYMENT_ORDER_NOT_CANCELLABLE);
        }

        // 멱등성 키 해제 — 재충전(새 키) 흐름 정상화. 커밋 후로 미뤄 롤백 시 키-DB 불일치 방지.
        scheduleUnmarkOnCommit(order.getIdempotencyKey());
    }

    /**
     * 멱등성 키 해제를 트랜잭션 커밋 이후로 예약한다. 커밋 실패(롤백) 시 키가 보존돼 멱등 가드가 유지된다.
     * 트랜잭션 컨텍스트 밖(직접 호출·테스트 등)에서는 즉시 해제.
     */
    private void scheduleUnmarkOnCommit(String idempotencyKey) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    idempotencyService.unmark(idempotencyKey);
                }
            });
        } else {
            idempotencyService.unmark(idempotencyKey);
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

        // size 필드는 실제 반환 개수가 아니라 요청 size를 그대로 echo한다(팀 표준, KAN-295 일관).
        return new CursorPageResponse<>(responses, nextCursor, hasNext, size);
    }

    /** 충전 직렬화 락 획득 — 대기 시간 내 실패하거나 인터럽트되면 PAYMENT_PROCESSING(503)으로 재시도 유도. */
    private void acquireChargeLock(RLock lock) {
        boolean locked;
        try {
            locked = lock.tryLock(CHARGE_LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentException(ErrorCode.PAYMENT_PROCESSING);
        }
        if (!locked) {
            throw new PaymentException(ErrorCode.PAYMENT_PROCESSING);
        }
    }

    /** 현재 스레드가 보유한 경우에만 해제 — 중복/비보유 해제 시 IllegalMonitorStateException 방지. */
    private static void unlockIfHeld(RLock lock) {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
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
