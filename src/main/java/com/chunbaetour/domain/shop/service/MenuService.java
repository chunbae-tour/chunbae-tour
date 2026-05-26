package com.chunbaetour.domain.shop.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.shop.dto.request.MenuCreateRequest;
import com.chunbaetour.domain.shop.dto.request.MenuUpdateRequest;
import com.chunbaetour.domain.shop.dto.response.MenuResponse;
import com.chunbaetour.domain.shop.entity.Menu;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.repository.MenuRepository;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.shop.type.ShopStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 메뉴 CRUD 서비스 (STORY-11).
 * 등록/수정/삭제 모두 ACTIVE 가게만 허용.
 * 메뉴 소유권은 shopId 매칭으로 검증 — 타 가게 menuId 접근 시 MENU_NOT_FOUND 반환.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MenuService {

    private final ShopRepository shopRepository;
    private final MenuRepository menuRepository;

    /**
     * 메뉴 등록.
     * ACTIVE 가게만 등록 가능. 가게 내 메뉴 이름 중복 불허 (trim 기준).
     */
    @Transactional
    public MenuResponse createMenu(Long userId, MenuCreateRequest request) {
        // userId로 내 가게 조회 — 가게 없으면 SHOP_001, 비활성이면 SHOP_005
        Shop shop = getActiveShop(userId);

        // 앞뒤 공백 trim — " 떡볶이 "와 "떡볶이"를 같은 이름으로 처리
        String normalizedName = request.name().trim();

        // 동일 가게 내 중복 메뉴 이름 차단 (@SQLRestriction으로 soft-deleted 메뉴 제외)
        if (menuRepository.existsByShopIdAndName(shop.getId(), normalizedName)) {
            throw new BusinessException(ErrorCode.MENU_DUPLICATE);
        }

        Menu menu = Menu.builder()
                .shopId(shop.getId())
                .name(normalizedName)
                .description(request.description())
                .price(request.price())
                .imageUrl(request.imageUrl())
                .build();

        return MenuResponse.from(menuRepository.save(menu));
    }

    /**
     * 메뉴 수정.
     * ACTIVE 가게만 수정 가능. null 필드는 기존 값 유지.
     * 다른 가게 메뉴 접근 시 MENU_NOT_FOUND (소유권 노출 방지).
     */
    @Transactional
    public MenuResponse updateMenu(Long userId, Long menuId, MenuUpdateRequest request) {
        // userId로 내 가게 조회 — 가게 없으면 SHOP_001, 비활성이면 SHOP_005
        Shop shop = getActiveShop(userId);

        // menuId + shopId 조합 조회 — 타 가게 메뉴는 MENU_NOT_FOUND로 처리
        Menu menu = getMenuOfShop(menuId, shop.getId());

        // 이름 변경 시 trim 후 중복 체크 — 자기 자신 이름은 허용
        if (request.name() != null) {
            String normalizedName = request.name().trim();
            if (!normalizedName.equals(menu.getName())
                    && menuRepository.existsByShopIdAndName(shop.getId(), normalizedName)) {
                throw new BusinessException(ErrorCode.MENU_DUPLICATE);
            }
        }

        menu.update(request);
        return MenuResponse.from(menu);
    }

    /**
     * 메뉴 삭제.
     * ACTIVE 가게만 삭제 가능. 본인 가게 메뉴만 삭제 가능.
     */
    @Transactional
    public void deleteMenu(Long userId, Long menuId) {
        // userId로 내 가게 조회 — 가게 없으면 SHOP_001, 비활성이면 SHOP_005
        Shop shop = getActiveShop(userId);

        // menuId + shopId 조합 조회 — 타 가게 메뉴는 MENU_NOT_FOUND로 처리
        Menu menu = getMenuOfShop(menuId, shop.getId());

        // hard delete 대신 soft delete — QR 결제 내역에서 menuId 참조 보존
        menu.softDelete();
    }

    /** 결제 내역 조회용 — soft-deleted 메뉴 포함 단건 조회 (QR 결제 영수증 메뉴명 표시). */
    public MenuResponse getMenuIncludingDeleted(Long menuId) {
        return menuRepository.findByIdIncludingDeleted(menuId)
                .map(MenuResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.MENU_NOT_FOUND));
    }

    /** userId로 ACTIVE 가게 조회. 없으면 SHOP_001, 비활성이면 SHOP_005. */
    private Shop getActiveShop(Long userId) {
        Shop shop = shopRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));

        if (shop.getStatus() != ShopStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.SHOP_INACTIVE);
        }

        return shop;
    }

    /** menuId + shopId 조합 조회. 없거나 다른 가게 소속이면 MENU_NOT_FOUND. */
    private Menu getMenuOfShop(Long menuId, Long shopId) {
        return menuRepository.findByIdAndShopId(menuId, shopId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MENU_NOT_FOUND));
    }
}
