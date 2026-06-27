package com.chunbaetour.domain.companion.dto.response;

import com.chunbaetour.domain.companion.entity.Companion;
import com.chunbaetour.domain.companion.entity.CompanionParticipant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record CompanionDetailResponse(
        Long companionId,
        Long chatRoomId,
        String status,
        LocalDate tripStartDate,
        LocalDate tripEndDate,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        List<ParticipantInfo> participants
) {

    public record ParticipantInfo(Long userId, LocalDateTime endedAt) {
        // CompanionParticipant → ParticipantInfo
        public static ParticipantInfo from(CompanionParticipant p) {
            return new ParticipantInfo(p.getUserId(), p.getEndedAt());
        }
    }

    // Companion + 참여자 목록으로 응답 생성
    public static CompanionDetailResponse of(Companion companion, List<CompanionParticipant> participants) {
        return new CompanionDetailResponse(
                companion.getId(),
                companion.getChatRoomId(),
                companion.getStatus().name(),
                companion.getTripStartDate(),
                companion.getTripEndDate(),
                companion.getStartedAt(),
                companion.getEndedAt(),
                participants.stream().map(ParticipantInfo::from).toList()
        );
    }
}
