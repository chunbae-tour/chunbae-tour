package com.chunbaetour.domain.payment.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record QrPayCreateRequest(
        @NotNull Long shopId,
        // @Size(max = 50): 상한 없으면 수천 개 menuId로 DB IN 쿼리 과부하 유발 가능 (DoS 방어).
        // 단일 QR 결제에서 서로 다른 메뉴 50종 이상은 비즈니스상 비현실적 — 충분히 넉넉한 상한선.
        @NotEmpty @Size(max = 50) @Valid List<QrPayItemRequest> menuItems
) {}
