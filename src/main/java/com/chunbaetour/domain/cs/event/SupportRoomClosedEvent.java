package com.chunbaetour.domain.cs.event;

// 상담 종료 — 방 소유자에게 SUPPORT_ROOM_CLOSED 알림 발송
public record SupportRoomClosedEvent(
        Long supportRoomId,
        Long userId  // 방 소유자 (USER·MERCHANT)
) {}
