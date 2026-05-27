package com.chunbaetour.domain.chat.event;

// 참여 신청 수락 — 신청자에게 알림 발송
public record JoinRequestApprovedEvent(
        Long chatRoomId,
        Long joinRequestId,
        Long applicantUserId
) {
}
