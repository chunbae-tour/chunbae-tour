package com.chunbaetour.domain.cs.dto.response;

import com.chunbaetour.domain.cs.entity.SupportMessage;
import com.chunbaetour.domain.cs.entity.SupportMessageType;
import com.chunbaetour.domain.cs.entity.SupportSenderRole;
import java.time.LocalDateTime;

public record SupportMessageResponse(
        Long messageId,
        Long senderId,
        SupportSenderRole senderRole,
        SupportMessageType messageType,
        String content,
        String fileUrl,
        LocalDateTime sentAt
) {
    public static SupportMessageResponse from(SupportMessage message) {
        return new SupportMessageResponse(
                message.getId(),
                message.getSenderId(),
                message.getSenderRole(),
                message.getMessageType(),
                message.getContent(),
                message.getFileUrl(),
                message.getSentAt()
        );
    }
}
