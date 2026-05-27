package com.chunbaetour.domain.shop.dto.response;

import com.chunbaetour.domain.shop.entity.Shop;

/** 상인 QR 코드 응답 — qrPayload를 프론트에서 QR 이미지로 렌더링 */
public record QrCodeResponse(Long shopId, String shopName, String qrPayload) {

    public static QrCodeResponse from(Shop shop) {
        return new QrCodeResponse(
                shop.getId(),
                shop.getShopName(),
                "YEOPJEON_PAY:SHOP:" + shop.getId()
        );
    }
}
