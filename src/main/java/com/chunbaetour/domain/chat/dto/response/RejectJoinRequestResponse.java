package com.chunbaetour.domain.chat.dto.response;

import com.chunbaetour.domain.chat.entity.JoinRequest;
import com.chunbaetour.domain.chat.type.JoinRequestStatus;

public record RejectJoinRequestResponse(
        Long joinRequestId,
        Long chatRoomId,
        JoinRequestStatus status
) {
    public static RejectJoinRequestResponse from(JoinRequest request) {
        return new RejectJoinRequestResponse(
                request.getId(),
                request.getChatRoomId(),
                request.getStatus()
        );
    }
}
