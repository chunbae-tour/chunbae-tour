package com.chunbaetour.domain.chat.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChatRoomDomainTest {

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_ID = 2L;

    // isOwnedBy() — ownerId 일치 시 true, 불일치 시 false
    @Test
    void isOwnedBy_ownerUserId_returnsTrue() {
        ChatRoom room = ChatRoom.createWithOwner(1L, OWNER_ID, "테스트방", null, 10);
        assertThat(room.isOwnedBy(OWNER_ID)).isTrue();
    }

    @Test
    void isOwnedBy_otherUserId_returnsFalse() {
        ChatRoom room = ChatRoom.createWithOwner(1L, OWNER_ID, "테스트방", null, 10);
        assertThat(room.isOwnedBy(OTHER_ID)).isFalse();
    }
}
