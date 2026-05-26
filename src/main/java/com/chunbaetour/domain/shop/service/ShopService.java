package com.chunbaetour.domain.shop.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.shop.dto.request.ShopUpdateRequest;
import com.chunbaetour.domain.shop.dto.response.QrCodeResponse;
import com.chunbaetour.domain.shop.dto.response.ShopInfoResponse;
import com.chunbaetour.domain.shop.dto.response.ShopResponse;
import com.chunbaetour.domain.shop.entity.Menu;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.repository.MenuRepository;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.shop.type.ShopStatus;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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
    private final ObjectMapper objectMapper;

    /**
     * 내 가게 조회.
     * userId로 가게를 조회 — 승인된 상인에게만 가게가 존재함.
     * 가게가 없으면 SHOP_001 예외.
     * SUSPENDED/CLOSED 상태도 조회 허용 — 상인이 본인 가게 상태 확인 가능해야 함.
     */
    public ShopResponse getMyShop(Long userId) {
        // userId로 내 가게 단건 조회 — 가게 없으면 SHOP_001
        Shop shop = shopRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));

        return ShopResponse.from(shop);
    }

    /**
     * 내 가게 정보 수정.
     * ACTIVE 상태 가게만 수정 가능 — SUSPENDED/CLOSED 시 SHOP_007.
     * null 필드는 기존 값 유지 (부분 수정 지원).
     * 위치(address/lat/lng)는 수정 불가 — 관리자에게 문의.
     */
    @Transactional
    public ShopResponse updateMyShop(Long userId, ShopUpdateRequest request) {
        // userId로 내 가게 조회 — 가게 없으면 SHOP_001
        Shop shop = shopRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));

        // ACTIVE 상태 가드 — SUSPENDED/CLOSED 가게는 수정 불가 (SHOP_007)
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
     * 가게가 없으면 SHOP_001. SUSPENDED/CLOSED여도 QR 확인 허용 — 결제 차단은 STORY-13에서 처리.
     */
    public QrCodeResponse getMyQrCode(Long userId) {
        // userId로 내 가게 조회 — 가게 없으면 SHOP_001
        Shop shop = shopRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));

        return QrCodeResponse.from(shop);
    }

    /**
     * 가게 공개 정보 + 메뉴 목록 조회 (비인증 공개).
     * QR 스캔·앱 탐색 등 진입 경로 무관. 실제 결제(POST /payments/qr)는 USER 인증 필수.
     * SUSPENDED/CLOSED 가게도 조회 허용 — 영업 종료 가게 정보도 열람 가능해야 함.
     * 삭제된 메뉴는 @SQLRestriction으로 자동 제외, isAvailable=false 메뉴는 포함 — 프론트에서 비활성 표시.
     */
    public ShopInfoResponse getShopInfo(Long shopId) {
        // shopId로 가게 조회 — 없으면 SHOP_001
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));

        // soft delete 제외된 메뉴 전체 조회 (@SQLRestriction 적용)
        List<Menu> menus = menuRepository.findByShopId(shopId);

        return ShopInfoResponse.from(shop, menus);
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
