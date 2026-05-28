package com.chunbaetour.domain.shop.dto.response;

import com.chunbaetour.domain.shop.entity.Settlement;
import com.chunbaetour.domain.shop.type.SettlementStatus;
import java.time.LocalDateTime;

/**
 * 관리자 전용 정산 응답 DTO.
 * 실제 계좌번호가 필요한 역할(수동 이체 처리)이므로 계좌번호 마스킹을 적용하지 않는다.
 * 로그·응답 바디에 계좌번호가 평문으로 포함되지 않도록 주의.
 */
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
