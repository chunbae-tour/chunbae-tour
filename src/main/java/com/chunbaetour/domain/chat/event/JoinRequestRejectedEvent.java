package com.chunbaetour.domain.chat.event;

// 참여 신청 거절 — 신청자에게 알림 발송
public record JoinRequestRejectedEvent(
        Long chatRoomId,
        Long joinRequestId,
        Long applicantUserId
) {
}
