package com.chunbaetour.domain.chat.type;

import java.util.List;

public enum ChatMemberState {
    OWNER_ACTIVE,   // 개설자 — 방 설정 변경·종료·강퇴 권한 보유
    MEMBER_ACTIVE,  // 일반 참여자
    MEMBER_LEFT,    // 자발적 퇴장 — 재참여 가능
    MEMBER_KICKED;  // 강퇴 — 재참여 불가 (CHAT_010 CHAT_MEMBER_KICKED_REJOIN)

    // 활성 멤버 상태 목록 — isActiveMember() 및 Repository IN 쿼리의 단일 정의
    public static List<ChatMemberState> activeStates() {
        return List.of(OWNER_ACTIVE, MEMBER_ACTIVE);
    }
}
