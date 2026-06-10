package com.chunbaetour.domain.companionreview.dto.response;

import com.chunbaetour.domain.companionreview.entity.Companion;
import java.time.LocalDateTime;

public record CompanionEndResponse(
        Long companionId,
        String status,
        LocalDateTime endedAt
) {
    // Companion 엔티티로부터 종료 응답 생성
    public static CompanionEndResponse from(Companion companion) {
        return new CompanionEndResponse(
                companion.getId(),
                companion.getStatus().name(),
                companion.getEndedAt()
        );
    }
}
