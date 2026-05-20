package com.chunbaetour.domain.chat.type;

public enum ChatRoomStatus {
    OPEN,    // 참여 가능 상태
    FULL,    // 정원 도달 — 참여 불가, 누군가 퇴장하면 OPEN 복귀 가능
    CLOSED   // 개설자가 종료 — 되돌릴 수 없음
}
