package com.chunbaetour.domain.payment.dto.response;

import com.chunbaetour.domain.payment.dto.response.QrPayCreateResponse.MenuSnapshotItem;
import java.time.LocalDateTime;
import java.util.List;

public record PendingQrPayResponse(
        String payRequestId,
        Long shopId,
        Long amount,
        List<MenuSnapshotItem> menuItems,
        LocalDateTime createdAt,
        LocalDateTime expiredAt
) {}
