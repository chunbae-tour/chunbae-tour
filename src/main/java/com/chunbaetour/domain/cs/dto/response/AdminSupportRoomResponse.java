package com.chunbaetour.domain.cs.dto.response;

import com.chunbaetour.domain.cs.entity.SupportRoom;
import com.chunbaetour.domain.cs.entity.SupportRoomStatus;
import java.time.LocalDateTime;

public record AdminSupportRoomResponse(
        Long supportRoomId,
        Long userId,
        String userNickname,
        SupportRoomStatus status,
        LastMessage lastMessage,
        LocalDateTime createdAt
) {
    // Admin 목록 마지막 메시지 요약
    public record LastMessage(String content, LocalDateTime sentAt) {}

    public static AdminSupportRoomResponse of(SupportRoom room, String userNickname, LastMessage lastMessage) {
        return new AdminSupportRoomResponse(
                room.getId(),
                room.getUserId(),
                userNickname,
                room.getStatus(),
                lastMessage,
                room.getCreatedAt()
        );
    }
}
