package com.chunbaetour.domain.shop.dto.response;

import com.chunbaetour.domain.shop.entity.Settlement;
import com.chunbaetour.domain.shop.type.SettlementStatus;
import java.time.LocalDateTime;

public record AdminSettlementResponse(
        Long settlementId,
        Long shopId,
        long amount,
        SettlementStatus status,
        String rejectReason,
        String bankName,
        String accountNumber,
        String accountHolder,
        LocalDateTime createdAt
) {
    public static AdminSettlementResponse from(Settlement s) {
        return new AdminSettlementResponse(
                s.getId(),
                s.getShopId(),
                s.getAmount(),
                s.getStatus(),
                s.getRejectReason(),
                s.getBankName(),
                s.getAccountNumber(),
                s.getAccountHolder(),
                s.getCreatedAt()
        );
    }
}
