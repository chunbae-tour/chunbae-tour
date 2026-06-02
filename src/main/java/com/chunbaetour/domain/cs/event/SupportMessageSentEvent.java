package com.chunbaetour.domain.cs.event;

// 상담 메시지 전송 — 수신 대상에게 SUPPORT_MESSAGE 알림 발송
public record SupportMessageSentEvent(
        Long supportRoomId,
        Long recipientUserId  // ADMIN 발신 → 방 소유자, USER/MERCHANT 발신 → 배정 ADMIN
) {}
