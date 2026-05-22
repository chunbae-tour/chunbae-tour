package com.chunbaetour.domain.chat.dto.response;

import com.chunbaetour.domain.chat.entity.JoinRequest;
import com.chunbaetour.domain.chat.type.JoinRequestStatus;

public record ApproveJoinRequestResponse(
        Long joinRequestId,
        Long chatRoomId,
        JoinRequestStatus status,
        int currentMembers
) {
    public static ApproveJoinRequestResponse from(JoinRequest request, int currentMembers) {
        return new ApproveJoinRequestResponse(
                request.getId(),
                request.getChatRoomId(),
                request.getStatus(),
                currentMembers
        );
    }
}
