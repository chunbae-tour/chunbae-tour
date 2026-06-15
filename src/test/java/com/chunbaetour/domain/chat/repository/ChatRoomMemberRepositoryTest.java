package com.chunbaetour.domain.chat.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.chunbaetour.domain.chat.entity.ChatRoom;
import com.chunbaetour.domain.chat.entity.ChatRoomMember;
import com.chunbaetour.domain.chat.type.ChatMemberState;
import com.chunbaetour.domain.support.AbstractIntegrationTest;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

// KAN-305 (user_id, member_state) 복합 인덱스 대상 쿼리(findMyRoomsWithCursor) 회귀 가드
@SpringBootTest
class ChatRoomMemberRepositoryTest extends AbstractIntegrationTest {

    @Autowired private ChatRoomMemberRepository chatRoomMemberRepository;
    @Autowired private ChatRoomRepository chatRoomRepository;

    @AfterEach
    void cleanup() {
        chatRoomMemberRepository.deleteAll();
        chatRoomRepository.deleteAll();
    }

    // OWNER_ACTIVE/MEMBER_ACTIVE 방만 반환, MEMBER_LEFT/MEMBER_KICKED 방은 제외
    @Test
    void findMyRoomsWithCursor_returnsOnlyActiveStates() {
        Long userId = 1L;

        ChatRoom ownerRoom = chatRoomRepository.save(
                ChatRoom.createWithOwner(nextPostId(), userId, "owner-room", "desc", 10));

        ChatRoom memberRoom = chatRoomRepository.save(
                ChatRoom.createWithOwner(nextPostId(), 999L, "member-room", "desc", 10));
        chatRoomMemberRepository.save(ChatRoomMember.ofMember(memberRoom, userId));

        ChatRoom leftRoom = chatRoomRepository.save(
                ChatRoom.createWithOwner(nextPostId(), 999L, "left-room", "desc", 10));
        ChatRoomMember leftMember = ChatRoomMember.ofMember(leftRoom, userId);
        leftMember.leave();
        chatRoomMemberRepository.save(leftMember);

        ChatRoom kickedRoom = chatRoomRepository.save(
                ChatRoom.createWithOwner(nextPostId(), 999L, "kicked-room", "desc", 10));
        ChatRoomMember kickedMember = ChatRoomMember.ofMember(kickedRoom, userId);
        kickedMember.kick();
        chatRoomMemberRepository.save(kickedMember);

        List<ChatRoomMember> result = chatRoomMemberRepository.findMyRoomsWithCursor(
                userId, ChatMemberState.activeStates(), Long.MAX_VALUE, PageRequest.of(0, 10));

        assertThat(result).extracting(m -> m.getChatRoom().getId())
                .containsExactlyInAnyOrder(ownerRoom.getId(), memberRoom.getId());
        assertThat(result).extracting(ChatRoomMember::getMemberState)
                .containsExactlyInAnyOrder(ChatMemberState.OWNER_ACTIVE, ChatMemberState.MEMBER_ACTIVE);
    }

    // c.id < cursorId 기준 커서 페이징, chatRoom.id DESC 정렬
    @Test
    void findMyRoomsWithCursor_cursorExcludesNewerRooms() {
        Long userId = 2L;

        ChatRoom room1 = chatRoomRepository.save(
                ChatRoom.createWithOwner(nextPostId(), userId, "room-1", "desc", 10));
        ChatRoom room2 = chatRoomRepository.save(
                ChatRoom.createWithOwner(nextPostId(), userId, "room-2", "desc", 10));
        ChatRoom room3 = chatRoomRepository.save(
                ChatRoom.createWithOwner(nextPostId(), userId, "room-3", "desc", 10));

        List<ChatRoomMember> result = chatRoomMemberRepository.findMyRoomsWithCursor(
                userId, ChatMemberState.activeStates(), room3.getId(), PageRequest.of(0, 10));

        assertThat(result).extracting(m -> m.getChatRoom().getId())
                .containsExactly(room2.getId(), room1.getId());
    }

    // 활성 상태인 방이 없으면 빈 리스트 반환
    @Test
    void findMyRoomsWithCursor_returnsEmpty_whenNoActiveRooms() {
        Long userId = 3L;

        ChatRoom room = chatRoomRepository.save(
                ChatRoom.createWithOwner(nextPostId(), 999L, "no-active-room", "desc", 10));
        ChatRoomMember member = ChatRoomMember.ofMember(room, userId);
        member.leave();
        chatRoomMemberRepository.save(member);

        List<ChatRoomMember> result = chatRoomMemberRepository.findMyRoomsWithCursor(
                userId, ChatMemberState.activeStates(), Long.MAX_VALUE, PageRequest.of(0, 10));

        assertThat(result).isEmpty();
    }

    private static final AtomicLong POST_ID_SEQUENCE = new AtomicLong(1_000_000L);

    private static long nextPostId() {
        return POST_ID_SEQUENCE.incrementAndGet();
    }
}
