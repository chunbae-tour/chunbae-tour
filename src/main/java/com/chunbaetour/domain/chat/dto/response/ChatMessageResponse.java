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
    // sender null: senderId != null → 탈퇴 계정 fallback, senderId == null → SYSTEM 메시지
    public static ChatMessageResponse from(Message message, Account sender) {
        String nickname = sender != null ? sender.getNickname()
                : (message.getSenderId() != null ? "탈퇴한 사용자" : null);
        String profileImageUrl = sender != null ? sender.getProfileImageUrl() : null;
        return new ChatMessageResponse(
                message.getId(),
                message.getChatRoomId(),
                message.getSenderId(),
                nickname,
                profileImageUrl,
                message.getMessageType(),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}
