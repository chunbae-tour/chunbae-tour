package com.chunbaetour.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.chat.entity.ChatRoom;
import com.chunbaetour.domain.chat.entity.ChatRoomMember;
import com.chunbaetour.domain.chat.repository.ChatRoomMemberRepository;
import com.chunbaetour.domain.chat.repository.ChatRoomRepository;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @Test
    void leaveRoom_member_succeeds() {
        // 일반 멤버(MEMBER_ACTIVE) 퇴장 — leave() 호출로 MEMBER_LEFT 전이, 인원 감소
        ChatRoom room = mock(ChatRoom.class);
        ChatRoomMember member = mock(ChatRoomMember.class);
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                .willReturn(Optional.of(member));

        chatRoomService.leaveRoom(USER_ID, ROOM_ID);

        verify(member).leave();
        verify(room).decrementMembers();
    }

    @Test
    void leaveRoom_nonExistentRoom_throws_CHAT_ROOM_NOT_FOUND() {
        // 존재하지 않는 방 퇴장 시도 — findById가 empty를 반환하면 CHAT_001 예외 발생
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> chatRoomService.leaveRoom(USER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND);
    }

    @Test
    void leaveRoom_notJoined_throws_CHAT_NOT_JOINED() {
        // 채팅방에 참여한 이력 없는 사용자 — 멤버 레코드 자체가 없으면 CHAT_005
        ChatRoom room = mock(ChatRoom.class);
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> chatRoomService.leaveRoom(USER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_NOT_JOINED);
    }

    @Test
    void leaveRoom_owner_throws_CHAT_OWNER_CANNOT_LEAVE() {
        // 방장(OWNER_ACTIVE)은 직접 퇴장 불가 — close()로만 방 종료 가능
        ChatRoom room = mock(ChatRoom.class);
        ChatRoomMember member = mock(ChatRoomMember.class);
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                .willReturn(Optional.of(member));
        doThrow(new BusinessException(ErrorCode.CHAT_OWNER_CANNOT_LEAVE)).when(member).leave();

        assertThatThrownBy(() -> chatRoomService.leaveRoom(USER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_OWNER_CANNOT_LEAVE);
    }

    @Test
    void leaveRoom_already_inactive_throws_CHAT_MEMBER_ALREADY_INACTIVE() {
        // 이미 퇴장(MEMBER_LEFT)하거나 강퇴(MEMBER_KICKED)된 멤버는 재퇴장 불가 — CHAT_016
        ChatRoom room = mock(ChatRoom.class);
        ChatRoomMember member = mock(ChatRoomMember.class);
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                .willReturn(Optional.of(member));
        doThrow(new BusinessException(ErrorCode.CHAT_MEMBER_ALREADY_INACTIVE)).when(member).leave();

        assertThatThrownBy(() -> chatRoomService.leaveRoom(USER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_MEMBER_ALREADY_INACTIVE);
    }
}
