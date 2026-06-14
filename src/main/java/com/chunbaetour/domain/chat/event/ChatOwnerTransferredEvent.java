package com.chunbaetour.domain.chat.event;

// 방장 위임 — 신규 방장에게 알림 발송
public record ChatOwnerTransferredEvent(
        Long chatRoomId,
        Long newOwnerId
) {
}
