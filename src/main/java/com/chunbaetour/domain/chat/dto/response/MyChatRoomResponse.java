package com.chunbaetour.domain.chat.dto.response;

import com.chunbaetour.domain.chat.entity.ChatRoomMember;
import com.chunbaetour.domain.chat.type.ChatRoomStatus;
import java.time.LocalDateTime;

public record MyChatRoomResponse(
        Long chatRoomId,
        String title,
        int currentMembers,
        int maxMembers,
        LastMessageInfo lastMessage,
        int unreadCount,
        ChatRoomStatus status
) {
    public record LastMessageInfo(
            String content,
            String senderNickname,
            LocalDateTime sentAt
    ) {}

    public static MyChatRoomResponse from(ChatRoomMember member) {
        var room = member.getChatRoom();
        return new MyChatRoomResponse(
                room.getId(),
                room.getTitle(),
                room.getCurrentMembers(),
                room.getMaxMembers(),
                null,  // TODO: S-12 메시지 구현 후 연동
                0,     // TODO: 읽음 추적 구현 후 연동
                room.getStatus()
        );
    }
}
