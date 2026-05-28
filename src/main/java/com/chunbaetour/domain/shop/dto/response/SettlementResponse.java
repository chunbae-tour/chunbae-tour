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
                maskAccountNumber(s.getAccountNumber()),
                s.getAccountHolder(),
                s.getCreatedAt()
        );
    }

    /** 계좌번호 마스킹 — 앞 3자리·뒤 4자리 외 * 처리. 예: 123-****-7890 */
    private static String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 7) {
            return accountNumber;
        }
        int len = accountNumber.length();
        return accountNumber.substring(0, 3)
                + "*".repeat(len - 7)
                + accountNumber.substring(len - 4);
    }
}
