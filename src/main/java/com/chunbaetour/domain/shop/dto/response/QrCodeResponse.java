package com.chunbaetour.domain.shop.dto.response;

import com.chunbaetour.domain.shop.entity.Shop;

/**
 * 상인 QR 코드 응답 — qrPayload를 프론트에서 QR 이미지로 렌더링 (KAN-253: nonce 포함).
 * payload 포맷: {@code YEOPJEON_PAY:SHOP:{shopId}:{qrNonce}}.
 * 프론트는 스캔 시 shopId·qrNonce를 파싱해 결제 요청에 함께 전달한다. 재발급되면 옛 nonce는 거절된다.
 * qrNonce null 여부 검증은 호출 서비스 레이어에서 수행한다.
 */
public record QrCodeResponse(Long shopId, String shopName, String qrPayload) {

    public static QrCodeResponse from(Shop shop) {
        return new QrCodeResponse(
                shop.getId(),
                shop.getShopName(),
                "YEOPJEON_PAY:SHOP:" + shop.getId() + ":" + shop.getQrNonce()
        );
    }
}
