package com.chunbaetour.domain.chat.event;

// 멤버 강퇴 — 강퇴 대상에게 알림 발송
public record ChatMemberKickedEvent(
        Long chatRoomId,
        Long kickedUserId
) {
}
