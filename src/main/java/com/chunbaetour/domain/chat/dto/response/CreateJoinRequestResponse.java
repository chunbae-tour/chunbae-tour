package com.chunbaetour.domain.chat.dto.response;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.chat.entity.JoinRequest;
import com.chunbaetour.domain.chat.type.JoinRequestStatus;
import com.chunbaetour.domain.community.common.WriterInfo;
import java.time.LocalDateTime;

public record CreateJoinRequestResponse(
        Long joinRequestId,
        Long chatRoomId,
        WriterInfo writer,
        String message,
        JoinRequestStatus status,
        LocalDateTime createdAt
) {
    public static CreateJoinRequestResponse from(JoinRequest request, Account account) {
        return new CreateJoinRequestResponse(
                request.getId(),
                request.getChatRoomId(),
                WriterInfo.from(account),
                request.getMessage(),
                request.getStatus(),
                request.getCreatedAt()
        );
    }
}
