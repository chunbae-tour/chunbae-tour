package com.chunbaetour.domain.payment.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record QrPayCreateRequest(
        @NotNull @Positive Long shopId,
        // QR payload(YEOPJEON_PAY:SHOP:{shopId}:{qrNonce})에서 파싱한 nonce — 현재 가게 nonce와 일치해야 결제 가능 (KAN-253).
        // 재발급으로 무효화된 옛 QR로 결제 시도 시 PAY_029로 거절.
        @NotBlank @Size(max = 36) String qrNonce,
        // @Size(max = 50): 상한 없으면 수천 개 menuId로 DB IN 쿼리 과부하 유발 가능 (DoS 방어).
        // 단일 QR 결제에서 서로 다른 메뉴 50종 이상은 비즈니스상 비현실적 — 충분히 넉넉한 상한선.
        @NotEmpty @Size(max = 50) @Valid List<QrPayItemRequest> menuItems
) {}
