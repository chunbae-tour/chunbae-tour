package com.chunbaetour.domain.payment.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.payment.dto.request.QrPayCreateRequest;
import com.chunbaetour.domain.payment.dto.request.QrPayItemRequest;
import com.chunbaetour.domain.payment.dto.response.QrPayCreateResponse;
import com.chunbaetour.domain.payment.dto.response.QrPayCreateResponse.MenuSnapshotItem;
import com.chunbaetour.domain.payment.entity.QrPayRequest;
import com.chunbaetour.domain.payment.repository.QrPayRequestRepository;
import com.chunbaetour.domain.payment.type.QrPayStatus;
import com.chunbaetour.domain.shop.entity.Menu;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.repository.MenuRepository;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.shop.type.ShopStatus;
import com.chunbaetour.domain.yeopjeon.entity.Wallet;
import com.chunbaetour.domain.yeopjeon.repository.WalletRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * QR 결제 요청 서비스 (STORY-13).
 * 사용자가 결제 요청을 생성하면 상인이 승인/거절할 때까지 PENDING 상태 유지.
 * 동시성 제어(분산 락·비관적 락)는 상인 승인 시(STORY-14)에서 처리.
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
    private final ObjectMapper objectMapper;
    private final Clock clock;

    private static final int QR_PAY_EXPIRY_MINUTES = 5;
    // pending_key unique 제약명 — DataIntegrityViolationException 원인 식별에 사용
    private static final String PENDING_KEY_CONSTRAINT = "pending_key";

    /**
     * QR 결제 요청 생성.
     * 결제 시점 메뉴 정보를 JSON 스냅샷으로 저장 — 이후 메뉴 수정/삭제 시에도 영수증에 당시 가격 보존.
     *
     * TODO(refactor): 메서드 책임 분리 — 현재 가게 검증·메뉴 검증·잔액 확인·저장 로직이 한 메서드에 집중됨.
     *   아래 private 헬퍼로 분리하면 각 단계 테스트 가독성·재사용성 향상:
     *     - validateShopPayable(shopId, userId)       : 가게 존재·ACTIVE·자가결제 검증
     *     - validateNoDuplicateMenuIds(menuItems)     : 중복 menuId 검사
     *     - buildMenuSnapshots(menuItems, shopId)     : 메뉴 조회·검증·스냅샷+금액 계산
     *     - validateWalletBalance(userId, totalAmount): 지갑 잔액 사전 체크
     *     - validateNoPendingRequest(userId, shopId)  : PENDING 중복 요청 사전 차단
     */
    @Transactional
    public QrPayCreateResponse createQrPayRequest(Long userId, QrPayCreateRequest request) {
        // 가게 존재 확인
        Shop shop = shopRepository.findById(request.shopId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));

        // ACTIVE 상태 가드 — SUSPENDED/CLOSED 가게는 결제 불가
        if (shop.getStatus() != ShopStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.SHOP_INACTIVE);
        }

        // 본인 가게 자가 결제 차단 — 실수·악용 모두 방지
        if (shop.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.SELF_PAYMENT_NOT_ALLOWED);
        }

        // 중복 menuId 검증
        // 정상 프론트라면 메뉴별 수량만 조절하므로 같은 menuId가 두 번 들어올 일 없음.
        // 단, 프론트 버그 또는 API 직접 호출 시 [{menuId:100, qty:2}, {menuId:100, qty:3}] 형태로
        // 중복 요청이 들어오면 스냅샷에 같은 메뉴가 2줄 생기고 totalAmount도 두 번 누적되어
        // 결제 금액이 의도와 다르게 계산됨. 서버에서 사전 차단.
        List<Long> menuIds = request.menuItems().stream()
                .map(QrPayItemRequest::menuId)
                .toList();
        Set<Long> uniqueMenuIds = new HashSet<>(menuIds);
        if (uniqueMenuIds.size() != menuIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        // 메뉴 일괄 조회 (N+1 방지)
        Map<Long, Menu> menuMap = menuRepository.findAllById(menuIds).stream()
                .collect(Collectors.toMap(Menu::getId, m -> m));

        // 메뉴별 검증 + 스냅샷 구성
        List<MenuSnapshotItem> snapshots = new ArrayList<>();
        long totalAmount = 0;
        for (QrPayItemRequest item : request.menuItems()) {
            Menu menu = menuMap.get(item.menuId());

            // 메뉴 존재 확인 — soft delete 메뉴는 @SQLRestriction으로 자동 제외
            if (menu == null) {
                throw new BusinessException(ErrorCode.MENU_NOT_FOUND);
            }
            // 다른 가게 메뉴 접근 차단
            if (!menu.getShopId().equals(request.shopId())) {
                throw new BusinessException(ErrorCode.MENU_NOT_FOUND);
            }
            // 품절/비활성 메뉴 결제 차단
            if (!menu.isAvailable()) {
                throw new BusinessException(ErrorCode.MENU_UNAVAILABLE);
            }
            // 메뉴 가격 음수/0 방어 — DB 데이터 오류 시 비정상 결제 생성 방지
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

        // totalAmount 0 이하 차단 — price <= 0 방어에도 불구하고 결제 금액 이상 발생 시 최종 방어
        if (totalAmount <= 0) {
            throw new BusinessException(ErrorCode.ZERO_AMOUNT_NOT_ALLOWED);
        }

        // 동일 사용자·가게에 PENDING 요청 사전 차단 — 일반 중복 요청에 명확한 에러 반환
        // DB unique 제약(pendingKey)은 동시 레이스 케이스 최종 방어용으로 병행 유지
        if (qrPayRequestRepository.existsByUserIdAndShopIdAndStatus(userId, shop.getId(), QrPayStatus.PENDING)) {
            throw new BusinessException(ErrorCode.DUPLICATE_QR_PAY_REQUEST);
        }

        // 결제 요청 시점 잔액 사전 체크 — 명백한 잔액 부족 조기 차단 (실제 차감은 상인 승인 시 STORY-14)
        // STORY-14에서 wallet row lock + 잔액 재검증 필수 (생성~승인 사이 잔액 변동 가능)
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));
        if (wallet.getBalance() < totalAmount) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
        }

        // 결제 시점 메뉴 정보 JSON 스냅샷 직렬화
        String menuItemsJson = serializeSnapshots(snapshots);

        // QrPayRequest 생성 — expiredAt = 현재 + 5분, 상인 미응답 시 STORY-15 스케줄러가 EXPIRED 처리
        LocalDateTime expiredAt = LocalDateTime.now(clock).plusMinutes(QR_PAY_EXPIRY_MINUTES);
        QrPayRequest qrPayRequest = QrPayRequest.create(
                UUID.randomUUID().toString(),
                userId,
                shop.getId(),
                totalAmount,
                menuItemsJson,
                expiredAt
        );
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
                qrPayRequest.getPayRequestId(),
                shop.getId(),
                shop.getShopName(),
                totalAmount,
                snapshots,
                expiredAt
        );
    }

    private String serializeSnapshots(List<MenuSnapshotItem> snapshots) {
        try {
            return objectMapper.writeValueAsString(snapshots);
        } catch (JacksonException e) {
            log.error("[QR 결제 요청] 메뉴 스냅샷 직렬화 실패", e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /** DataIntegrityViolationException 예외 체인에 pending_key 제약명이 포함되는지 확인 */
    private boolean containsPendingKeyConstraint(DataIntegrityViolationException e) {
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
