package com.chunbaetour.domain.chat.dto.response;

import com.chunbaetour.domain.chat.entity.ChatRoom;
import com.chunbaetour.domain.chat.entity.JoinRequest;
import com.chunbaetour.domain.chat.type.JoinRequestStatus;
import java.time.LocalDateTime;

public record MyJoinRequestResponse(
        Long joinRequestId,
        Long chatRoomId,
        String chatRoomTitle,
        String message,
        JoinRequestStatus status,
        LocalDateTime createdAt
) {
    // chatRoom null — 방 삭제 엣지케이스, chatRoomTitle null 처리
    public static MyJoinRequestResponse from(JoinRequest request, ChatRoom chatRoom) {
        return new MyJoinRequestResponse(
                request.getId(),
                request.getChatRoomId(),
                chatRoom != null ? chatRoom.getTitle() : null,
                request.getMessage(),
                request.getStatus(),
                request.getCreatedAt()
        );
    }
}
