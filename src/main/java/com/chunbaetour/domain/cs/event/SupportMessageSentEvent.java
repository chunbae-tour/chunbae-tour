package com.chunbaetour.domain.cs.event;

// ADMIN이 상담 메시지 발신 시 방 소유자(USER·MERCHANT)에게 SUPPORT_MESSAGE 알림 발송
public record SupportMessageSentEvent(
        Long supportRoomId,
        Long recipientUserId  // 방 소유자 userId (USER·MERCHANT)
) {}
