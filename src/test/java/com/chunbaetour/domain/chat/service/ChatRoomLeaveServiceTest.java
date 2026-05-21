package com.chunbaetour.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.chat.entity.ChatRoom;
import com.chunbaetour.domain.chat.entity.ChatRoomMember;
import com.chunbaetour.domain.chat.repository.ChatRoomMemberRepository;
import com.chunbaetour.domain.chat.repository.ChatRoomRepository;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatRoomLeaveServiceTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepository;

    @InjectMocks
    private ChatRoomService chatRoomService;

    private static final Long USER_ID = 2L;
    private static final Long ROOM_ID = 100L;

    private ChatRoom room;

    @BeforeEach
    void setUp() {
        room = mock(ChatRoom.class);
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
    }

    @Test
    void leaveRoom_member_succeeds() {
        // 일반 멤버(MEMBER_ACTIVE) 퇴장 — leave() → decrementMembers() 순서 보장
        ChatRoomMember member = mock(ChatRoomMember.class);
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                .willReturn(Optional.of(member));

        chatRoomService.leaveRoom(USER_ID, ROOM_ID);

        // 상태 전이 후 인원 감소 순서여야 함 — 역순이면 FULL→OPEN 전환이 먼저 일어나는 버그 가능
        InOrder inOrder = inOrder(member, room);
        inOrder.verify(member).leave();
        inOrder.verify(room).decrementMembers();
    }

    @Test
    void leaveRoom_nonExistentRoom_throws_CHAT_ROOM_NOT_FOUND() {
        // 존재하지 않는 방 — findById empty 시 CHAT_001
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> chatRoomService.leaveRoom(USER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> extractErrorCode(ex))
                .isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND);
    }

    @Test
    void leaveRoom_notJoined_throws_CHAT_NOT_JOINED() {
        // 멤버 레코드 없는 사용자 — CHAT_005
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> chatRoomService.leaveRoom(USER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> extractErrorCode(ex))
                .isEqualTo(ErrorCode.CHAT_NOT_JOINED);
    }

    @Test
    void leaveRoom_owner_throws_CHAT_OWNER_CANNOT_LEAVE() {
        // 방장(OWNER_ACTIVE)은 직접 퇴장 불가 — close()로만 방 종료 가능
        ChatRoomMember member = mock(ChatRoomMember.class);
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                .willReturn(Optional.of(member));
        doThrow(new BusinessException(ErrorCode.CHAT_OWNER_CANNOT_LEAVE)).when(member).leave();

        assertThatThrownBy(() -> chatRoomService.leaveRoom(USER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> extractErrorCode(ex))
                .isEqualTo(ErrorCode.CHAT_OWNER_CANNOT_LEAVE);

        // leave() 실패 시 인원 감소가 일어나면 안 됨
        verify(room, never()).decrementMembers();
    }

    @Test
    void leaveRoom_already_inactive_throws_CHAT_MEMBER_ALREADY_INACTIVE() {
        // 이미 퇴장(MEMBER_LEFT)하거나 강퇴(MEMBER_KICKED)된 멤버 재퇴장 불가 — CHAT_016
        ChatRoomMember member = mock(ChatRoomMember.class);
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                .willReturn(Optional.of(member));
        doThrow(new BusinessException(ErrorCode.CHAT_MEMBER_ALREADY_INACTIVE)).when(member).leave();

        assertThatThrownBy(() -> chatRoomService.leaveRoom(USER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> extractErrorCode(ex))
                .isEqualTo(ErrorCode.CHAT_MEMBER_ALREADY_INACTIVE);

        // leave() 실패 시 인원 감소가 일어나면 안 됨
        verify(room, never()).decrementMembers();
    }

    private ErrorCode extractErrorCode(Throwable ex) {
        return ((BusinessException) ex).getErrorCode();
    }
}
