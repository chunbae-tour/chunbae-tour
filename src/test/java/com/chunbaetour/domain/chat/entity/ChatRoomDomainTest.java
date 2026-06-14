package com.chunbaetour.domain.chat.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
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

    // transferOwner() — ownerId 교체
    @Test
    void transferOwner_changesOwnerId() {
        ChatRoom room = ChatRoom.createWithOwner(1L, OWNER_ID, "테스트방", null, 10);
        room.transferOwner(OTHER_ID);
        assertThat(room.isOwnedBy(OTHER_ID)).isTrue();
        assertThat(room.isOwnedBy(OWNER_ID)).isFalse();
    }

    // transferOwner() — 현재 방장 본인에게 위임 시도 시 CHAT_019
    @Test
    void transferOwner_self_throws_CHAT_OWNER_TRANSFER_INVALID_TARGET() {
        ChatRoom room = ChatRoom.createWithOwner(1L, OWNER_ID, "테스트방", null, 10);
        assertThatThrownBy(() -> room.transferOwner(OWNER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_OWNER_TRANSFER_INVALID_TARGET);
    }
}
