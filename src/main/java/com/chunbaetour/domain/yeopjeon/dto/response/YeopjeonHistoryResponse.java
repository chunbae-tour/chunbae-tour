package com.chunbaetour.domain.yeopjeon.dto.response;

import com.chunbaetour.domain.yeopjeon.entity.YeopjeonHistory;
import com.chunbaetour.domain.yeopjeon.type.YeopjeonHistoryType;
import java.time.LocalDateTime;

/** 엽전 이력 단건 응답 DTO. balanceSnapshot은 이 이력 발생 직후 잔액. */
public record YeopjeonHistoryResponse(
        Long id,
        YeopjeonHistoryType type,
        Long amount,
        Long balanceSnapshot,
        String description,
        LocalDateTime createdAt
) {
    public static YeopjeonHistoryResponse from(YeopjeonHistory history) {
        return new YeopjeonHistoryResponse(
                history.getId(),
                history.getType(),
                displayAmount(history),
                history.getBalanceSnapshot(),
                history.getDescription(),
                history.getCreatedAt()
        );
    }

    private static Long displayAmount(YeopjeonHistory history) {
        return switch (history.getType()) {
            case CHARGE, RECEIVED_PAYMENT -> Math.abs(history.getAmount());
            case PAYMENT, REFUND -> -Math.abs(history.getAmount());
        };
    }
}
