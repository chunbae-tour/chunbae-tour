package com.chunbaetour.domain.shop.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.shop.dto.request.ShopUpdateRequest;
import com.chunbaetour.domain.shop.dto.response.QrCodeResponse;
import com.chunbaetour.domain.shop.dto.response.ShopInfoResponse;
import com.chunbaetour.domain.shop.dto.response.ShopResponse;
import com.chunbaetour.domain.shop.dto.response.ShopWalletResponse;
import com.chunbaetour.domain.shop.entity.Menu;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.entity.ShopWallet;
import com.chunbaetour.domain.shop.repository.MenuRepository;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.shop.repository.ShopWalletRepository;
import com.chunbaetour.domain.shop.type.ShopStatus;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상인 가게 서비스 (STORY-10).
 * 내 가게 조회(GET), 가게 정보 수정(PATCH) 담당.
 * 위치(address/lat/lng)는 수정 불가 — 관리자 처리 영역.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShopService {

    private final ShopRepository shopRepository;
    private final MenuRepository menuRepository;
    private final ShopWalletRepository shopWalletRepository;
    private final ObjectMapper objectMapper;

    /**
     * 내 가게 목록 조회.
     * 상인은 여러 가게를 운영할 수 있으므로 전체 목록 반환.
     * SUSPENDED/CLOSED 가게도 포함 — 본인 가게 상태 확인 가능해야 함.
     */
    public List<ShopResponse> getMyShops(Long userId) {
        // userId로 내 가게 목록 조회
        return shopRepository.findAllByUserId(userId)
                .stream().map(ShopResponse::from).toList();
    }

    /**
     * 내 가게 단건 조회.
     * shopId + userId 조합으로 소유권 검증 — 타인 가게 접근 시 SHOP_001.
     * SUSPENDED/CLOSED 상태도 조회 허용 — 상인이 본인 가게 상태 확인 가능해야 함.
     */
    public ShopResponse getMyShop(Long userId, Long shopId) {
        // shopId + userId 조합으로 본인 가게 조회
        Shop shop = shopRepository.findByIdAndUserId(shopId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));

        return ShopResponse.from(shop);
    }

    /**
     * 내 가게 정보 수정.
     * ACTIVE 상태 가게만 수정 가능 — SUSPENDED/CLOSED 시 SHOP_005.
     * null 필드는 기존 값 유지 (부분 수정 지원).
     * 위치(address/lat/lng)는 수정 불가 — 관리자에게 문의.
     */
    @Transactional
    public ShopResponse updateMyShop(Long userId, Long shopId, ShopUpdateRequest request) {
        // 서비스 직접 호출 대비 null 방어
        if (request == null) throw new BusinessException(ErrorCode.INVALID_REQUEST);

        // shopId + userId 조합으로 본인 가게 조회
        Shop shop = shopRepository.findByIdAndUserId(shopId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));

        // ACTIVE 상태 가드 — SUSPENDED/CLOSED 가게는 수정 불가 (SHOP_005)
        if (shop.getStatus() != ShopStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.SHOP_INACTIVE);
        }

        // imageUrls JSON 유효성 검사 — 형식 오류 시 DB flush에서 MySQL 5xx 발생하므로 사전 차단
        validateImageUrls(request.imageUrls());

        // 수정 가능한 필드 업데이트 (위치 제외)
        shop.update(request);

        return ShopResponse.from(shop);
    }

    /**
     * 내 가게 QR 코드 payload 조회.
     * qrPayload = "YEOPJEON_PAY:SHOP:{shopId}" — 클라이언트가 이 문자열로 QR 이미지 렌더링.
     * SUSPENDED/CLOSED여도 QR 확인 허용 — 결제 차단은 STORY-13에서 처리.
     */
    public QrCodeResponse getMyQrCode(Long userId, Long shopId) {
        // shopId + userId 조합으로 본인 가게 조회
        Shop shop = shopRepository.findByIdAndUserId(shopId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));

        return QrCodeResponse.from(shop);
    }

    /**
     * 가게 수익 지갑 조회.
     * 본인 가게 소유권 확인 후 ShopWallet 잔액 반환.
     */
    public ShopWalletResponse getShopWallet(Long userId, Long shopId) {
        // Security 레이어에서 인증 필수이지만 서비스 경계 방어로 null 명시 차단
        if (userId == null) throw new BusinessException(ErrorCode.SHOP_NOT_FOUND);
        // 소유권 확인 — 타인 가게 접근 시 SHOP_001
        shopRepository.findByIdAndUserId(shopId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));

        // ShopWallet 조회 — approve() 시 Shop과 함께 생성되므로 없으면 불변식 위반
        ShopWallet wallet = shopWalletRepository.findByShopId(shopId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_WALLET_NOT_FOUND));

        return ShopWalletResponse.from(wallet);
    }

    /**
     * 가게 공개 정보 + 메뉴 목록 조회 (비인증 공개).
     * QR 스캔·앱 탐색 등 진입 경로 무관. 실제 결제(POST /payments/qr)는 USER 인증 필수.
     * CLOSED 가게는 조회 허용 — 영업 종료 가게 정보도 열람 가능해야 함.
     * SUSPENDED 가게는 차단 — 관리자 신고 정지, 존재 여부 노출 방지로 SHOP_NOT_FOUND 통일.
     * 삭제된 메뉴는 @SQLRestriction으로 자동 제외, isAvailable=false 메뉴는 포함 — 프론트에서 비활성 표시.
     */
    public ShopInfoResponse getShopInfo(Long shopId) {
        // shopId로 가게 조회 — 없으면 SHOP_001
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));

        // SUSPENDED 가게는 공개 노출 차단 — 관리자 신고 처리 정지 (SHOP_001로 통일, 존재 여부 노출 방지)
        // CLOSED는 기존 정책 유지(조회 허용) — SUSPENDED만 차단
        if (shop.getStatus() == ShopStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.SHOP_NOT_FOUND);
        }

        // soft delete 제외된 메뉴 전체 조회 (@SQLRestriction 적용)
        List<Menu> menus = menuRepository.findByShopIdOrderByIdAsc(shopId);

        return ShopInfoResponse.from(shop, menus);
    }

    /**
     * 상인 직접 가게 상태 전환 (KAN-213). ACTIVE ↔ CLOSED만 허용.
     * SUSPENDED 요청 시 SHOP_STATUS_FORBIDDEN. 현재 SUSPENDED 가게는 SHOP_INACTIVE.
     */
    @Transactional
    public void updateMyShopStatus(Long userId, Long shopId, ShopStatus newStatus) {
        // shopId + userId 조합으로 본인 가게 조회
        Shop shop = shopRepository.findByIdAndUserId(shopId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));

        // 도메인 메서드에서 SUSPENDED 전환 차단 및 SUSPENDED 상태 가드 처리
        shop.merchantChangeStatus(newStatus);
    }

    /**
     * 관리자 가게 상태 변경 (ACTIVE ↔ SUSPENDED).
     * CLOSED 상태 가게 변경 불가 — SHOP_INACTIVE.
     * CLOSED로 변경 불가 — INVALID_INPUT_VALUE (폐업은 별도 처리).
     */
    @Transactional
    public void updateShopStatus(Long shopId, ShopStatus newStatus) {
        if (newStatus == ShopStatus.CLOSED) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));
        // 도메인 가드(CLOSED 불변) 우회 방지 — 명시적 전이 메서드로 위임
        // CLOSED는 초반 guard에서 이미 차단 — switch는 ACTIVE/SUSPENDED만 처리
        switch (newStatus) {
            case ACTIVE -> shop.activate();
            case SUSPENDED -> shop.hide();
        }
    }

    /**
     * 신고 처리: 가게 숨김 (shopId 기준).
     * report 도메인이 ShopRepository를 직접 참조하지 않도록 위임 진입점 역할.
     */
    @Transactional
    public void hideShop(Long shopId) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));
        shop.hide();
    }

    /**
     * REVOKE_MERCHANT 신고 처리: owner의 모든 활성 가게를 일괄 SUSPENDED.
     * CLOSED(폐업) 가게는 이미 운영 종료 상태이므로 skip — hide() 호출 시 SHOP_INACTIVE 예외 방지.
     * 다중 가게 운영 상인의 계정 단위 권한 회수 시 호출.
     */
    @Transactional
    public void hideAllShopsByOwnerId(Long ownerId) {
        shopRepository.findAllByUserId(ownerId).stream()
                .filter(shop -> shop.getStatus() != ShopStatus.CLOSED)
                .forEach(Shop::hide);
    }

    /**
     * shopId → 상인 accountId(userId) 반환.
     * 신고 처리(REVOKE_MERCHANT) 및 신고 대상 자기신고 검증에 사용.
     * 가게 없으면 Optional.empty() — 에러 코드는 호출 측에서 결정.
     */
    public Optional<Long> findMerchantAccountId(Long shopId) {
        return shopRepository.findById(shopId).map(Shop::getUserId);
    }

    /** imageUrls가 JSON 배열인지 검사 — null이면 수정 안 함으로 통과, 배열 아닌 JSON(객체·문자열 등)도 거부 */
    private void validateImageUrls(String imageUrls) {
        if (imageUrls == null) return;
        try {
            var node = objectMapper.readTree(imageUrls);
            if (!node.isArray()) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
            // S3 URL 배열이므로 원소는 반드시 문자열이어야 함 — [1, true, null, {...}] 등 거부
            for (var item : node) {
                if (!item.isTextual() || item.asText().isBlank()) {
                    throw new BusinessException(ErrorCode.INVALID_REQUEST);
                }
            }
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }
}
