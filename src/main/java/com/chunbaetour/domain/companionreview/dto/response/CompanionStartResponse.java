package com.chunbaetour.domain.companionreview.dto.response;

import com.chunbaetour.domain.companionreview.entity.Companion;
import java.time.LocalDateTime;
import java.util.List;

public record CompanionStartResponse(
        Long companionId,
        Long chatRoomId,
        String status,
        List<Long> participantUserIds,
        LocalDateTime startedAt
) {
    // Companion 엔티티 + 참여자 목록으로 응답 생성
    public static CompanionStartResponse of(Companion companion, List<Long> participantUserIds) {
        return new CompanionStartResponse(
                companion.getId(),
                companion.getChatRoomId(),
                companion.getStatus().name(),
                participantUserIds,
                companion.getStartedAt()
        );
    }
}
