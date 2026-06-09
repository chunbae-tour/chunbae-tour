package com.chunbaetour.domain.chat.event;

import java.util.List;

// 메시지 전송 — ACTIVE 멤버(발신자 제외)에게 알림 발송
public record ChatMessageSentEvent(
        Long chatRoomId,
        Long senderId,
        List<Long> recipientUserIds
) {
}
