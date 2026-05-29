package com.chunbaetour.domain.shop.dto.response;

import com.chunbaetour.domain.shop.entity.Settlement;
import com.chunbaetour.domain.shop.type.SettlementStatus;
import java.time.LocalDateTime;

public record SettlementResponse(
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
    public static SettlementResponse from(Settlement s) {
        return new SettlementResponse(
                s.getId(),
                s.getShopId(),
                s.getAmount(),
                s.getStatus(),
                s.getRejectReason(),
                s.getBankName(),
                maskAccountNumber(s.getAccountNumber()),
                s.getAccountHolder(),
                s.getCreatedAt()
        );
    }

    /** 계좌번호 마스킹 — 앞 3자리·뒤 4자리 숫자 외 * 처리, 하이픈 위치 유지. 예: 123-****-7890 */
    private static String maskAccountNumber(String accountNumber) {
        if (accountNumber == null) {
            return null;
        }
        String digits = accountNumber.replaceAll("[^0-9]", "");
        if (digits.length() <= 7) {
            return accountNumber;
        }

        // 앞에서 3번째 숫자의 원본 인덱스
        int digitCount = 0;
        int prefixEnd = -1;
        for (int i = 0; i < accountNumber.length(); i++) {
            if (Character.isDigit(accountNumber.charAt(i)) && ++digitCount == 3) {
                prefixEnd = i;
                break;
            }
        }

        // 뒤에서 4번째 숫자의 원본 인덱스
        digitCount = 0;
        int suffixStart = -1;
        for (int i = accountNumber.length() - 1; i >= 0; i--) {
            if (Character.isDigit(accountNumber.charAt(i)) && ++digitCount == 4) {
                suffixStart = i;
                break;
            }
        }

        if (prefixEnd < 0 || suffixStart < 0 || prefixEnd >= suffixStart) {
            return accountNumber;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < accountNumber.length(); i++) {
            char c = accountNumber.charAt(i);
            if (i <= prefixEnd || i >= suffixStart) {
                sb.append(c);
            } else {
                // 중간 구간: 숫자는 *, 하이픈은 유지
                sb.append(c == '-' ? '-' : '*');
            }
        }
        return sb.toString();
    }
}
