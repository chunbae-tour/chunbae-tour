package com.chunbaetour.domain.shop.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.shop.dto.request.ShopUpdateRequest;
import com.chunbaetour.domain.shop.dto.response.ShopResponse;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.shop.type.ShopStatus;
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

        // 수정 가능한 필드 업데이트 (위치 제외)
        shop.update(request);

        return ShopResponse.from(shop);
    }
}
