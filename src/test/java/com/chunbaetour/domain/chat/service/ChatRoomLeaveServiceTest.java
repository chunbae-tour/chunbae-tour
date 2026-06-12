package com.chunbaetour.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.chat.entity.ChatRoom;
import com.chunbaetour.domain.chat.entity.ChatRoomMember;
import com.chunbaetour.domain.chat.repository.ChatRoomMemberRepository;
import com.chunbaetour.domain.chat.repository.ChatRoomRepository;
import com.chunbaetour.domain.chat.type.ChatRoomStatus;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;

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
        InOrder inOrder = inOrder(member, room, chatRoomRepository);
        inOrder.verify(member).leave();
        inOrder.verify(room).decrementMembers();
        // saveAndFlush로 낙관적 잠금 실패를 메서드 내부에서 처리하므로 반드시 호출되어야 함
        inOrder.verify(chatRoomRepository).saveAndFlush(room);
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
    void leaveRoom_ownerWithOtherActiveMembers_throws_CHAT_OWNER_CANNOT_LEAVE() {
        // 방장 + 다른 ACTIVE 멤버 존재 — 위임 선행 필요(CHAT_015), leave()/close() 호출되지 않아야 함
        ChatRoomMember member = mock(ChatRoomMember.class);
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                .willReturn(Optional.of(member));
        given(member.isOwner()).willReturn(true);
        given(room.getCurrentMembers()).willReturn(2);

        assertThatThrownBy(() -> chatRoomService.leaveRoom(USER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> extractErrorCode(ex))
                .isEqualTo(ErrorCode.CHAT_OWNER_CANNOT_LEAVE);

        verify(member, never()).leave();
        verify(room, never()).close();
        verify(room, never()).decrementMembers();
    }

    @Test
    void leaveRoom_soleOwner_closesRoom() {
        // 단독 방장(다른 ACTIVE 멤버 없음) 퇴장 시도 — 위임 없이 방 자동 CLOSED
        ChatRoomMember member = mock(ChatRoomMember.class);
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                .willReturn(Optional.of(member));
        given(member.isOwner()).willReturn(true);
        given(room.getCurrentMembers()).willReturn(1);

        chatRoomService.leaveRoom(USER_ID, ROOM_ID);

        verify(room).close();
        verify(chatRoomRepository).saveAndFlush(room);
        verify(member, never()).leave();
        verify(room, never()).decrementMembers();
    }

    @Test
    void leaveRoom_soleOwner_concurrentModification_throws_CONCURRENT_UPDATE() {
        // 단독 방장 자동 CLOSED 처리 중 @Version 충돌 — CONCURRENT_UPDATE
        ChatRoomMember member = mock(ChatRoomMember.class);
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                .willReturn(Optional.of(member));
        given(member.isOwner()).willReturn(true);
        given(room.getCurrentMembers()).willReturn(1);
        willThrow(ObjectOptimisticLockingFailureException.class)
                .given(chatRoomRepository).saveAndFlush(room);

        assertThatThrownBy(() -> chatRoomService.leaveRoom(USER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> extractErrorCode(ex))
                .isEqualTo(ErrorCode.CONCURRENT_UPDATE);
    }

    @Test
    void leaveRoom_closedRoomOwner_leavesAsRegularMember() {
        // CLOSED 방의 방장 퇴장 — 방장 권한 무의미, 일반 멤버와 동일하게 leave() + decrementMembers() 처리, close() 미호출
        ChatRoomMember member = mock(ChatRoomMember.class);
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                .willReturn(Optional.of(member));
        given(member.isOwner()).willReturn(true);
        given(room.getStatus()).willReturn(ChatRoomStatus.CLOSED);

        chatRoomService.leaveRoom(USER_ID, ROOM_ID);

        InOrder inOrder = inOrder(member, room, chatRoomRepository);
        inOrder.verify(member).leave();
        inOrder.verify(room).decrementMembers();
        inOrder.verify(chatRoomRepository).saveAndFlush(room);
        verify(room, never()).close();
    }

    @Test
    void leaveRoom_soleOwner_autoCloseRace_throws_CHAT_ROOM_CLOSED() {
        // 단독 방장 자동 CLOSED 처리 중 동시 close()가 먼저 커밋 완료된 경우 — 재조회 시 CLOSED 확인 후 CHAT_013
        ChatRoomMember member = mock(ChatRoomMember.class);
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, USER_ID))
                .willReturn(Optional.of(member));
        given(member.isOwner()).willReturn(true);
        given(room.getStatus()).willReturn(ChatRoomStatus.OPEN, ChatRoomStatus.CLOSED);
        given(room.getCurrentMembers()).willReturn(1);
        willThrow(ObjectOptimisticLockingFailureException.class)
                .given(chatRoomRepository).saveAndFlush(room);

        assertThatThrownBy(() -> chatRoomService.leaveRoom(USER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> extractErrorCode(ex))
                .isEqualTo(ErrorCode.CHAT_ROOM_CLOSED);
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
