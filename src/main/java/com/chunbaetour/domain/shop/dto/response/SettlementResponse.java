package com.chunbaetour.domain.shop.dto.response;

import com.chunbaetour.domain.shop.entity.Settlement;
import com.chunbaetour.domain.shop.type.SettlementStatus;
import java.time.LocalDateTime;

public record SettlementResponse(
        Long settlementId,
        long amount,
        SettlementStatus status,
        String rejectReason,
        String bankName,
        String accountNumber,
        String accountHolder,
        LocalDateTime createdAt
) {
    public static SettlementResponse from(Settlement s) {
        return new SettlementResponse(
                s.getId(),
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
