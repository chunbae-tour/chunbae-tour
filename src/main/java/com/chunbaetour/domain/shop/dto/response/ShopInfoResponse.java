package com.chunbaetour.domain.shop.dto.response;

import com.chunbaetour.domain.shop.entity.Menu;
import com.chunbaetour.domain.shop.entity.Shop;
import java.util.List;

/** 가게 공개 정보 + 메뉴 목록 응답 — 비인증 공개 */
public record ShopInfoResponse(Long shopId, String shopName, String category, List<MenuResponse> menus) {

    public static ShopInfoResponse from(Shop shop, List<Menu> menus) {
        return new ShopInfoResponse(
                shop.getId(),
                shop.getShopName(),
                shop.getCategory(),
                menus.stream().map(MenuResponse::from).toList()
        );
    }
}
