package com.chunbaetour.domain.chat.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chunbaetour.domain.chat.type.ChatMemberState;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
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

    // promoteToOwner() — MEMBER_ACTIVE만 OWNER_ACTIVE로 전환 가능
    @Test
    void promoteToOwner_memberActive_becomesOwnerActive() {
        ChatRoomMember member = ChatRoomMember.ofMember(room, 2L);
        member.promoteToOwner();
        assertThat(member.isOwner()).isTrue();
    }

    @Test
    void promoteToOwner_alreadyOwner_throws_CHAT_OWNER_TRANSFER_INVALID_TARGET() {
        ChatRoomMember owner = ChatRoomMember.ofOwner(room, 1L);
        assertThatThrownBy(owner::promoteToOwner)
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_OWNER_TRANSFER_INVALID_TARGET);
    }

    @Test
    void promoteToOwner_inactiveMember_throws_CHAT_OWNER_TRANSFER_INVALID_TARGET() {
        ChatRoomMember member = ChatRoomMember.ofMember(room, 2L);
        member.leave();
        assertThatThrownBy(member::promoteToOwner)
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_OWNER_TRANSFER_INVALID_TARGET);
    }

    // demoteFromOwner() — OWNER_ACTIVE만 MEMBER_ACTIVE로 전환 가능
    @Test
    void demoteFromOwner_ownerActive_becomesMemberActive() {
        ChatRoomMember owner = ChatRoomMember.ofOwner(room, 1L);
        owner.demoteFromOwner();
        assertThat(owner.isOwner()).isFalse();
        assertThat(owner.isActiveMember()).isTrue();
    }

    @Test
    void demoteFromOwner_notOwner_throws_INVALID_REQUEST() {
        ChatRoomMember member = ChatRoomMember.ofMember(room, 2L);
        assertThatThrownBy(member::demoteFromOwner)
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    // ChatMemberState.activeStates() — isActiveMember()와 정의 일치 확인
    @Test
    void activeStates_containsExactlyOwnerActiveAndMemberActive() {
        assertThat(ChatMemberState.activeStates())
                .containsExactlyInAnyOrder(ChatMemberState.OWNER_ACTIVE, ChatMemberState.MEMBER_ACTIVE);
    }
}
