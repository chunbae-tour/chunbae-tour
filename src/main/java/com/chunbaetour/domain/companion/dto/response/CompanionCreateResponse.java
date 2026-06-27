package com.chunbaetour.domain.companion.dto.response;

import com.chunbaetour.domain.companion.entity.Companion;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record CompanionCreateResponse(
        Long companionId,
        Long chatRoomId,
        String status,
        List<Long> participantUserIds,
        LocalDateTime createdAt,
        LocalDate tripStartDate,
        LocalDate tripEndDate
) {
    // Companion 엔티티 + 참여자 목록으로 응답 생성
    public static CompanionCreateResponse of(Companion companion, List<Long> participantUserIds) {
        return new CompanionCreateResponse(
                companion.getId(),
                companion.getChatRoomId(),
                companion.getStatus().name(),
                participantUserIds,
                companion.getStartedAt(),
                companion.getTripStartDate(),
                companion.getTripEndDate()
        );
    }
}
