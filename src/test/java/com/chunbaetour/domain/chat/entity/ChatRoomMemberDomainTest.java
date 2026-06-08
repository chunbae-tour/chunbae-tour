package com.chunbaetour.domain.chat.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.chunbaetour.domain.chat.type.ChatMemberState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChatRoomMemberDomainTest {

    private ChatRoom room;

    @BeforeEach
    void setUp() {
        room = ChatRoom.createWithOwner(1L, 1L, "테스트방", null, 10);
    }

    // isActiveMember() — OWNER_ACTIVE, MEMBER_ACTIVE만 true
    @Test
    void isActiveMember_ownerActive_returnsTrue() {
        ChatRoomMember member = ChatRoomMember.ofOwner(room, 1L);
        assertThat(member.isActiveMember()).isTrue();
    }

    @Test
    void isActiveMember_memberActive_returnsTrue() {
        ChatRoomMember member = ChatRoomMember.ofMember(room, 2L);
        assertThat(member.isActiveMember()).isTrue();
    }

    @Test
    void isActiveMember_memberLeft_returnsFalse() {
        ChatRoomMember member = ChatRoomMember.ofMember(room, 2L);
        member.leave();
        assertThat(member.isActiveMember()).isFalse();
    }

    @Test
    void isActiveMember_memberKicked_returnsFalse() {
        ChatRoomMember member = ChatRoomMember.ofMember(room, 2L);
        member.kick();
        assertThat(member.isActiveMember()).isFalse();
    }

    // isKicked() — MEMBER_KICKED만 true
    @Test
    void isKicked_memberKicked_returnsTrue() {
        ChatRoomMember member = ChatRoomMember.ofMember(room, 2L);
        member.kick();
        assertThat(member.isKicked()).isTrue();
    }

    @Test
    void isKicked_memberActive_returnsFalse() {
        ChatRoomMember member = ChatRoomMember.ofMember(room, 2L);
        assertThat(member.isKicked()).isFalse();
    }

    @Test
    void isKicked_memberLeft_returnsFalse() {
        ChatRoomMember member = ChatRoomMember.ofMember(room, 2L);
        member.leave();
        assertThat(member.isKicked()).isFalse();
    }

    // ChatMemberState.activeStates() — isActiveMember()와 정의 일치 확인
    @Test
    void activeStates_containsExactlyOwnerActiveAndMemberActive() {
        assertThat(ChatMemberState.activeStates())
                .containsExactlyInAnyOrder(ChatMemberState.OWNER_ACTIVE, ChatMemberState.MEMBER_ACTIVE);
    }
}
