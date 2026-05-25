package com.chunbaetour.domain.chat.dto.response;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.chat.entity.Message;
import com.chunbaetour.domain.chat.type.MessageType;
import java.time.LocalDateTime;

// WebSocket 구독자에게 브로드캐스트되는 메시지 응답 DTO
public record ChatMessageResponse(
        Long messageId,
        Long chatRoomId,
        Long senderId,
        String senderNickname,
        String senderProfileImageUrl,
        MessageType messageType,
        String content,
        LocalDateTime sentAt
) {
    // 저장 후 즉시 브로드캐스트 — sentAt은 DB 저장 시점 기준
    public static ChatMessageResponse from(Message message, Account sender) {
        return new ChatMessageResponse(
                message.getId(),
                message.getChatRoomId(),
                message.getSenderId(),
                sender.getNickname(),
                sender.getProfileImageUrl(),
                message.getMessageType(),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}
