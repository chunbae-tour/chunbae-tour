package com.chunbaetour.domain.payment.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.payment.dto.request.QrPayCreateRequest;
import com.chunbaetour.domain.payment.dto.request.QrPayItemRequest;
import com.chunbaetour.domain.payment.dto.response.QrPayCreateResponse;
import com.chunbaetour.domain.payment.dto.response.QrPayCreateResponse.MenuSnapshotItem;
import com.chunbaetour.domain.payment.entity.QrPayRequest;
import com.chunbaetour.domain.payment.repository.QrPayRequestRepository;
import com.chunbaetour.domain.shop.entity.Menu;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.repository.MenuRepository;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.shop.type.ShopStatus;
import com.chunbaetour.domain.yeopjeon.entity.Wallet;
import com.chunbaetour.domain.yeopjeon.repository.WalletRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * QR 결제 요청 서비스 (STORY-13).
 * 사용자가 결제 요청을 생성하면 상인이 승인/거절할 때까지 PENDING 상태 유지.
 * 동시성 제어(분산 락·비관적 락)는 상인 승인 시(STORY-14)에서 처리.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QrPayService {

    private final ShopRepository shopRepository;
    private final MenuRepository menuRepository;
    private final QrPayRequestRepository qrPayRequestRepository;
    private final WalletRepository walletRepository;
    private final ObjectMapper objectMapper;

    private static final int QR_PAY_EXPIRY_MINUTES = 5;

    /**
     * QR 결제 요청 생성.
     * 결제 시점 메뉴 정보를 JSON 스냅샷으로 저장 — 이후 메뉴 수정/삭제 시에도 영수증에 당시 가격 보존.
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

        // 요청 menuId 목록으로 메뉴 일괄 조회 (N+1 방지)
        List<Long> menuIds = request.menuItems().stream()
                .map(QrPayItemRequest::menuId)
                .toList();
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

            snapshots.add(new MenuSnapshotItem(menu.getId(), menu.getName(), menu.getPrice(), item.quantity()));
            totalAmount += menu.getPrice() * item.quantity();
        }

        // 결제 요청 시점 잔액 사전 체크 — 명백한 잔액 부족 조기 차단 (실제 차감은 상인 승인 시 STORY-14)
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));
        if (wallet.getBalance() < totalAmount) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
        }

        // 결제 시점 메뉴 정보 JSON 스냅샷 직렬화
        String menuItemsJson = serializeSnapshots(snapshots);

        // QrPayRequest 생성 — expiredAt = 현재 + 5분, 상인 미응답 시 STORY-15 스케줄러가 EXPIRED 처리
        LocalDateTime expiredAt = LocalDateTime.now().plusMinutes(QR_PAY_EXPIRY_MINUTES);
        QrPayRequest qrPayRequest = QrPayRequest.create(
                UUID.randomUUID().toString(),
                userId,
                shop.getId(),
                totalAmount,
                menuItemsJson,
                expiredAt
        );
        qrPayRequestRepository.save(qrPayRequest);

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
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
