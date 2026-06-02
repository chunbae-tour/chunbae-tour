package com.chunbaetour.domain.payment.service;

import static com.chunbaetour.domain.common.redis.MerchantHomeCacheKeys.CACHE_KEY_PREFIX;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.payment.dto.request.QrPayConfirmRequest;
import com.chunbaetour.domain.payment.dto.request.QrPayCreateRequest;
import com.chunbaetour.domain.payment.dto.request.QrPayItemRequest;
import com.chunbaetour.domain.payment.dto.response.PendingQrPayResponse;
import com.chunbaetour.domain.payment.dto.response.QrPayCreateResponse;
import com.chunbaetour.domain.payment.dto.response.QrPayStatusResponse;
import com.chunbaetour.domain.payment.dto.response.QrPayCreateResponse.MenuSnapshotItem;
import com.chunbaetour.domain.payment.entity.QrPayRequest;
import com.chunbaetour.domain.payment.repository.QrPayRequestRepository;
import com.chunbaetour.domain.payment.type.QrPayStatus;
import com.chunbaetour.domain.shop.entity.Menu;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.entity.ShopWallet;
import com.chunbaetour.domain.shop.repository.MenuRepository;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.shop.repository.ShopWalletRepository;
import com.chunbaetour.domain.shop.type.ShopStatus;
import com.chunbaetour.domain.yeopjeon.entity.Wallet;
import com.chunbaetour.domain.yeopjeon.entity.YeopjeonHistory;
import com.chunbaetour.domain.yeopjeon.repository.WalletRepository;
import com.chunbaetour.domain.yeopjeon.repository.YeopjeonHistoryRepository;
import com.chunbaetour.domain.yeopjeon.type.YeopjeonHistoryType;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * QR 결제 서비스 (STORY-13, STORY-14).
 * STORY-13: 사용자가 결제 요청 생성 → PENDING 상태.
 * STORY-14: 상인이 승인/거절 — 승인 시 분산 락 + 비관적 락으로 잔액 차감·증가.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QrPayService {

    private final ShopRepository shopRepository;
    private final MenuRepository menuRepository;
    private final QrPayRequestRepository qrPayRequestRepository;
    private final WalletRepository walletRepository;
    private final ShopWalletRepository shopWalletRepository;
    private final YeopjeonHistoryRepository yeopjeonHistoryRepository;
    private final RedissonClient redissonClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final MessageSource messageSource;

    private static final int QR_PAY_EXPIRY_MINUTES = 5;
    /** PENDING QR 결제 조회 LIMIT — PENDING은 실시간 승인 대상이므로 50 초과는 비정상 상황 */
    private static final int PENDING_QR_LIMIT = 50;
    /** 분산 락 키: qr:lock:{shopId}:{userId} — 동일 사용자·가게 동시 승인 시도 직렬화 */
    private static final String QR_LOCK_KEY = "qr:lock:%d:%d";
    private static final int LOCK_WAIT_SECONDS = 3;
    // pending_key unique 제약명 — DataIntegrityViolationException 원인 식별에 사용
    private static final String PENDING_KEY_CONSTRAINT = "pending_key";

    /**
     * QR 결제 요청 생성.
     * 결제 시점 메뉴 정보를 JSON 스냅샷으로 저장 — 이후 메뉴 수정/삭제 시에도 영수증에 당시 가격 보존.
     */
    @Transactional
    public QrPayCreateResponse createQrPayRequest(Long userId, QrPayCreateRequest request) {
        Shop shop = shopRepository.findById(request.shopId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));
        validateShop(shop, userId);

        MenuSnapshotResult snapshot = buildMenuSnapshot(request.menuItems(), request.shopId());

        // 동일 사용자·가게에 유효한 PENDING 요청 사전 차단 — 만료된 PENDING은 제외 (스케줄러 60초 지연 대응)
        // DB unique 제약(pendingKey)은 동시 레이스 케이스 최종 방어용으로 병행 유지
        if (qrPayRequestRepository.existsByUserIdAndShopIdAndStatusAndExpiredAtAfter(
                userId, shop.getId(), QrPayStatus.PENDING, LocalDateTime.now(clock))) {
            throw new BusinessException(ErrorCode.DUPLICATE_QR_PAY_REQUEST);
        }

        validateWalletBalance(userId, snapshot.totalAmount());

        // 결제 시점 메뉴 정보 JSON 스냅샷 직렬화
        // 생성 이후 메뉴 가격 변경/삭제 시에도 결제 금액은 생성 시점 기준으로 고정됨 (의도된 정책)
        String menuItemsJson = serializeSnapshots(snapshot.snapshots());

        // QrPayRequest 생성 — expiredAt = 현재 + 5분, 상인 미응답 시 STORY-15 스케줄러가 EXPIRED 처리
        LocalDateTime expiredAt = LocalDateTime.now(clock).plusMinutes(QR_PAY_EXPIRY_MINUTES);
        QrPayRequest qrPayRequest = QrPayRequest.create(
                UUID.randomUUID().toString(), userId, shop.getId(),
                snapshot.totalAmount(), menuItemsJson, expiredAt);
        // pending_key unique 위반 = 동시 요청 레이스 케이스 → DUPLICATE
        // 그 외 무결성 오류(pay_request_id 충돌 등)는 내부 서버 오류로 처리
        try {
            qrPayRequestRepository.saveAndFlush(qrPayRequest);
        } catch (DataIntegrityViolationException e) {
            if (containsPendingKeyConstraint(e)) {
                throw new BusinessException(ErrorCode.DUPLICATE_QR_PAY_REQUEST);
            }
            log.error("[QR 결제 요청] 예상치 못한 DB 무결성 오류 발생", e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        return new QrPayCreateResponse(
                qrPayRequest.getPayRequestId(), shop.getId(), shop.getShopName(),
                snapshot.totalAmount(), snapshot.snapshots(), expiredAt);
    }

    // ACTIVE 상태 가드 + 자가 결제 차단
    private void validateShop(Shop shop, Long userId) {
        if (shop.getStatus() != ShopStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.SHOP_INACTIVE);
        }
        if (shop.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.SELF_PAYMENT_NOT_ALLOWED);
        }
    }

    /**
     * 메뉴 중복 검증 + 일괄 조회 + 스냅샷 구성 + totalAmount 계산.
     * 프론트 버그·직접 호출로 같은 menuId가 두 번 들어오면 금액이 두 번 누적되므로 서버에서 사전 차단.
     */
    private MenuSnapshotResult buildMenuSnapshot(List<QrPayItemRequest> items, Long shopId) {
        List<Long> menuIds = items.stream().map(QrPayItemRequest::menuId).toList();
        if (new HashSet<>(menuIds).size() != menuIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        // N+1 방지: 메뉴 일괄 조회
        Map<Long, Menu> menuMap = menuRepository.findAllById(menuIds).stream()
                .collect(Collectors.toMap(Menu::getId, m -> m));

        List<MenuSnapshotItem> snapshots = new ArrayList<>();
        long totalAmount = 0;
        for (QrPayItemRequest item : items) {
            Menu menu = menuMap.get(item.menuId());
            // soft delete 메뉴는 @SQLRestriction으로 자동 제외, 다른 가게 메뉴 접근 차단
            if (menu == null || !menu.getShopId().equals(shopId)) {
                throw new BusinessException(ErrorCode.MENU_NOT_FOUND);
            }
            if (!menu.isAvailable()) {
                throw new BusinessException(ErrorCode.MENU_UNAVAILABLE);
            }
            // DB 데이터 오류 시 비정상 결제 생성 방지
            if (menu.getPrice() <= 0) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
            snapshots.add(new MenuSnapshotItem(menu.getId(), menu.getName(), menu.getPrice(), item.quantity()));
            try {
                long itemTotal = Math.multiplyExact(menu.getPrice(), (long) item.quantity());
                totalAmount = Math.addExact(totalAmount, itemTotal);
            } catch (ArithmeticException e) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
        }

        // price <= 0 방어에도 불구하고 결제 금액 이상 발생 시 최종 방어
        if (totalAmount <= 0) {
            throw new BusinessException(ErrorCode.ZERO_AMOUNT_NOT_ALLOWED);
        }
        return new MenuSnapshotResult(snapshots, totalAmount);
    }

    // 결제 요청 시점 잔액 사전 체크 — 실제 차감은 상인 승인 시 wallet row lock + 재검증 (STORY-14)
    private void validateWalletBalance(Long userId, long totalAmount) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));
        if (wallet.getBalance() < totalAmount) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
        }
    }

    private record MenuSnapshotResult(List<MenuSnapshotItem> snapshots, long totalAmount) {}

    /**
     * QR 결제 승인/거절 (STORY-14, MERCHANT 전용).
     *
     * <p>APPROVE 흐름:
     * 1. 결제 요청 조회 및 소유권 확인
     * 2. 분산 락 획득 (qr:lock:{shopId}:{userId}, 대기 3s, watchdog 자동 갱신)
     * 3. 비관적 락으로 사용자 지갑 → 상인 지갑 순서 고정 조회 (데드락 방지)
     * 4. 잔액 검증 후 차감·증가
     * 5. 이력 기록 (사용자: PAYMENT, 상인: RECEIVED_PAYMENT)
     * 6. 상태 COMPLETED 전이
     *
     * <p>REJECT 흐름: 분산 락 없이 상태만 REJECTED 전이.
     */
    @Transactional
    public void confirmQrPayRequest(Long merchantUserId, String payRequestId, QrPayConfirmRequest request) {
        // 결제 요청 조회
        QrPayRequest qrPayRequest = qrPayRequestRepository.findByPayRequestId(payRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QR_PAY_REQUEST_NOT_FOUND));

        // 소유권 확인 — 해당 가게가 이 상인의 가게인지 검증
        Shop shop = shopRepository.findById(qrPayRequest.getShopId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));
        if (!shop.getUserId().equals(merchantUserId)) {
            throw new BusinessException(ErrorCode.QR_PAY_CONFIRM_FORBIDDEN);
        }
        // 승인 시점 가게 영업 상태 재검증 — 생성~승인 사이 정지/폐업 처리된 가게 결제 차단
        if (shop.getStatus() != ShopStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.SHOP_INACTIVE);
        }

        // 빠른 실패 — 락 대기 전 명백한 만료는 조기 차단 (최종 검증은 락 이후 재조회 기준)
        if (LocalDateTime.now(clock).isAfter(qrPayRequest.getExpiredAt())) {
            throw new BusinessException(ErrorCode.QR_PAY_INVALID_STATUS_TRANSITION);
        }

        // 승인·거절 모두 분산 락 이후 처리 — 동시 호출 직렬화 및 stale 상태 재처리 방지
        // watchdog 모드: lease time 없이 호출 → Redisson이 30초마다 자동 갱신, 스레드 종료 시 자동 해제
        RLock lock = redissonClient.getLock(
                QR_LOCK_KEY.formatted(qrPayRequest.getShopId(), qrPayRequest.getUserId()));
        try {
            if (!lock.tryLock(LOCK_WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new BusinessException(ErrorCode.PAYMENT_PROCESSING);
            }

            // 락 획득 후 재조회 — 대기 중 다른 요청이 이미 처리했을 수 있음
            QrPayRequest lockedRequest = qrPayRequestRepository.findByPayRequestIdWithLock(payRequestId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.QR_PAY_REQUEST_NOT_FOUND));
            // fast-fail: 이미 완료/거절된 요청은 불필요한 락·IO 낭비 없이 즉시 차단
            if (lockedRequest.getStatus() != QrPayStatus.PENDING) {
                throw new BusinessException(ErrorCode.QR_PAY_INVALID_STATUS_TRANSITION);
            }
            // 만료 체크 — APPROVE·REJECT 모두 차단. 만료 상태 전이는 STORY-15 스케줄러 전담
            LocalDateTime now = LocalDateTime.now(clock);
            if (now.isAfter(lockedRequest.getExpiredAt())) {
                throw new BusinessException(ErrorCode.QR_PAY_INVALID_STATUS_TRANSITION);
            }

            if (request.action() == QrPayConfirmRequest.Action.REJECT) {
                lockedRequest.reject(request.rejectReason());
                return;
            }

            // 비관적 락 순서 고정: wallets → shop_wallets (데드락 방지)
            Wallet wallet = walletRepository.findByUserIdWithLock(lockedRequest.getUserId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));
            ShopWallet shopWallet = shopWalletRepository.findByShopIdWithLock(lockedRequest.getShopId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_WALLET_NOT_FOUND));

            // 잔액 검증 — 요청 시점 이후 잔액이 줄었을 수 있음
            long amount = lockedRequest.getAmount();
            if (wallet.getBalance() < amount) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
            }

            // 잔액 차감·증가
            wallet.debit(amount);
            shopWallet.credit(amount);

            // 이력 기록 — payRequestId 연결로 QR 결제 원본 요청 역추적 가능 (KAN-206)
            yeopjeonHistoryRepository.save(YeopjeonHistory.createForQrPay(
                    lockedRequest.getUserId(), lockedRequest.getShopId(), payRequestId,
                    YeopjeonHistoryType.PAYMENT, amount, wallet.getBalance(),
                    messageSource.getMessage("history.payment.description",
                            new Object[]{shop.getShopName()}, LocaleContextHolder.getLocale())
            ));
            // merchantUserId == shop.getUserId() 는 소유권 검증으로 보장됨 — 위치 변경 시 재검증 필요
            yeopjeonHistoryRepository.save(YeopjeonHistory.createForQrPay(
                    merchantUserId, lockedRequest.getShopId(), payRequestId,
                    YeopjeonHistoryType.RECEIVED_PAYMENT, amount, shopWallet.getBalance(),
                    messageSource.getMessage("history.received.description",
                            null, LocaleContextHolder.getLocale())
            ));

            // 상태 COMPLETED 전이 — 완료 시각은 updatedAt과 별도로 고정 보존
            lockedRequest.complete(now);
            invalidateMerchantHomeCache(merchantUserId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.PAYMENT_PROCESSING);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * QR 결제 상태 폴링 (STORY-13, USER 전용).
     * 푸시 알림 미도달 시 사용자가 결제 완료 여부를 확인하는 수단.
     * 본인 요청만 조회 가능 — 타인 payRequestId로 조회 시 NOT_FOUND 처리.
     */
    @Transactional(readOnly = true)
    public QrPayStatusResponse getQrPayStatus(Long userId, String payRequestId) {
        QrPayRequest qrPayRequest = qrPayRequestRepository.findByPayRequestId(payRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QR_PAY_REQUEST_NOT_FOUND));

        // 본인 요청만 조회 가능 — 타인 요청 노출 차단
        if (!qrPayRequest.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.QR_PAY_REQUEST_NOT_FOUND);
        }

        return QrPayStatusResponse.from(qrPayRequest);
    }

    /**
     * 상인 대기중 QR 결제 목록 조회.
     * 상인 소유 가게 전체의 PENDING 요청을 만료 시각 오름차순으로 반환.
     * LIMIT 50 — 동시 PENDING이 과도하게 누적되는 비정상 상황에서 페이로드 폭발 방지.
     * 50 초과 건은 다음 polling 호출에서 순차 처리됨 (만료 임박순 우선).
     */
    @Transactional(readOnly = true)
    public List<PendingQrPayResponse> getPendingQrPayments(Long merchantUserId) {
        // 서비스 경계 방어 검증 — @AuthenticationPrincipal 외 직접 호출 경로 보호
        if (merchantUserId == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        // 상인 소유 가게 ID 목록 조회
        List<Long> shopIds = shopRepository.findAllByUserId(merchantUserId)
                .stream().map(Shop::getId).toList();
        if (shopIds.isEmpty()) {
            return List.of();
        }
        // 미만료 PENDING 결제 요청 조회 — expiredAt > now로 스케줄러 60초 지연 구간 만료 건 제외
        return qrPayRequestRepository.findPendingByShopIds(
                        shopIds, QrPayStatus.PENDING, LocalDateTime.now(clock), Pageable.ofSize(PENDING_QR_LIMIT))
                .stream()
                .map(req -> PendingQrPayResponse.from(req, deserializeMenuItems(req.getMenuItems())))
                .toList();
    }

    private List<MenuSnapshotItem> deserializeMenuItems(String menuItemsJson) {
        try {
            return objectMapper.readValue(menuItemsJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, MenuSnapshotItem.class));
        } catch (JacksonException e) {
            // 서버 생성 스냅샷이므로 정상 상황에서는 발생하지 않음.
            // 스키마 변경·수동 DB 수정 등 예외 상황에서 한 건 실패가 전체 목록 500으로 번지지 않도록 빈 리스트 폴백.
            log.warn("[QR 결제 대기 목록] 메뉴 스냅샷 역직렬화 실패 — 해당 건 menuItems 빈 리스트 처리. json={}", menuItemsJson);
            log.debug("stack trace:", e);
            return List.of();
        }
    }

    /**
     * 상인 홈 캐시를 무효화한다.
     * 승인 완료 직후 최신 매출과 최근 결제가 바로 보이도록 best-effort로 삭제한다.
     */
    private void invalidateMerchantHomeCache(Long merchantUserId) {
        try {
            redisTemplate.delete(CACHE_KEY_PREFIX + merchantUserId);
        } catch (Exception e) {
            log.warn("[QR 결제 승인] 상인 홈 캐시 무효화 실패 (merchantUserId: {})", merchantUserId, e);
        }
    }

    private String serializeSnapshots(List<MenuSnapshotItem> snapshots) {
        try {
            return objectMapper.writeValueAsString(snapshots);
        } catch (JacksonException e) {
            log.error("[QR 결제 요청] 메뉴 스냅샷 직렬화 실패", e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /** pending_key unique 제약 위반 여부 확인
     * Hibernate 7 getConstraintName() 우선 — 드라이버 메시지 포맷 독립적.
     * null 반환 시 메시지 문자열 fallback으로 이중 방어. */
    private boolean containsPendingKeyConstraint(DataIntegrityViolationException e) {
        // Hibernate ConstraintViolationException에서 제약명 직접 추출 (Hibernate 7+)
        if (e.getCause() instanceof org.hibernate.exception.ConstraintViolationException hce) {
            String constraintName = hce.getConstraintName();
            if (constraintName != null) {
                return constraintName.contains(PENDING_KEY_CONSTRAINT);
            }
        }
        // fallback: getConstraintName() null 반환 시 메시지 문자열 기반 확인
        Throwable cause = e;
        while (cause != null) {
            String message = cause.getMessage();
            if (message != null && message.contains(PENDING_KEY_CONSTRAINT)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
