package com.chunbaetour.domain.chat.dto.response;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.chat.entity.JoinRequest;
import java.time.LocalDateTime;

public record CreateJoinRequestResponse(
        Long joinRequestId,
        Long chatRoomId,
        Long userId,
        String nickname,
        String profileImageUrl,
        float companionScore,
        String message,
        String status,
        LocalDateTime createdAt
) {
    public static CreateJoinRequestResponse from(JoinRequest request, Account account) {
        return new CreateJoinRequestResponse(
                request.getId(),
                request.getChatRoomId(),
                request.getUserId(),
                account.getNickname(),
                account.getProfileImageUrl(),
                account.getCompanionScore(),
                request.getMessage(),
                request.getStatus().name(),
                request.getCreatedAt()
        );
    }
}
