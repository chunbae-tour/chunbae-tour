package com.chunbaetour.domain.chat.event;

// 참여 신청 생성 — 방장에게 알림 발송
public record JoinRequestCreatedEvent(
        Long chatRoomId,
        Long joinRequestId,
        Long ownerUserId
) {
}
