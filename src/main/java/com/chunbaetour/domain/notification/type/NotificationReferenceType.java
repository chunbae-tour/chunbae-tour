package com.chunbaetour.domain.notification.type;

public enum NotificationReferenceType {
    CHAT_ROOM,    // 채팅방 (referenceId = chatRoomId)
    JOIN_REQUEST, // 참여 신청 (referenceId = joinRequestId)
    SUPPORT_ROOM  // 고객센터 상담방 (referenceId = supportRoomId)
}
