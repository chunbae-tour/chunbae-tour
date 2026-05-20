package com.chunbaetour.domain.chat.type;

public enum JoinRequestStatus {
    PENDING,   // 개설자 처리 대기 중
    APPROVED,  // 승인됨 — ChatRoomMember 생성 트리거
    REJECTED   // 거부됨
}
