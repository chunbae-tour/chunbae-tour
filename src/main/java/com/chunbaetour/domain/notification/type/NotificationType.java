package com.chunbaetour.domain.notification.type;

public enum NotificationType {
    CHAT_MESSAGE,        // 동행 채팅 새 메시지
    CHAT_JOIN_REQUEST,   // 채팅방 참여 신청
    CHAT_JOIN_APPROVED,  // 참여 신청 승인
    CHAT_JOIN_REJECTED,  // 참여 신청 거절
    CHAT_MEMBER_KICKED,  // 채팅방 강퇴
    SUPPORT_MESSAGE,     // 고객센터 상담 새 메시지
    SUPPORT_ROOM_CLOSED  // 고객센터 상담 종료
}
