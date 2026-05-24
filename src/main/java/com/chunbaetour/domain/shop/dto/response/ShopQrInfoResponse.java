package com.chunbaetour.domain.shop.dto.response;

import com.chunbaetour.domain.shop.entity.Menu;
import com.chunbaetour.domain.shop.entity.Shop;
import java.util.List;

/** 가게 공개 정보 + 메뉴 목록 응답 — QR 스캔·앱 탐색 경로 무관, 비인증 공개 */
public record ShopQrInfoResponse(Long shopId, String shopName, String category, List<MenuResponse> menus) {

    public static ShopQrInfoResponse from(Shop shop, List<Menu> menus) {
        return new ShopQrInfoResponse(
                shop.getId(),
                shop.getShopName(),
                shop.getCategory(),
                menus.stream().map(MenuResponse::from).toList()
        );
    }
}
