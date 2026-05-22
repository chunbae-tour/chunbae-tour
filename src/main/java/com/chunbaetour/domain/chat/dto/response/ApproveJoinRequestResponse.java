package com.chunbaetour.domain.chat.dto.response;

import com.chunbaetour.domain.chat.entity.JoinRequest;
import com.chunbaetour.domain.chat.type.JoinRequestStatus;

public record ApproveJoinRequestResponse(
        Long joinRequestId,
        Long chatRoomId,
        Long applicantId,
        JoinRequestStatus status
) {
    public static ApproveJoinRequestResponse from(JoinRequest request) {
        return new ApproveJoinRequestResponse(
                request.getId(),
                request.getChatRoomId(),
                request.getUserId(),
                request.getStatus()
        );
    }
}
