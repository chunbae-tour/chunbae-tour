package com.chunbaetour.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.chat.entity.ChatRoom;
import com.chunbaetour.domain.chat.entity.ChatRoomMember;
import com.chunbaetour.domain.chat.event.ChatOwnerTransferredEvent;
import com.chunbaetour.domain.chat.repository.ChatRoomMemberRepository;
import com.chunbaetour.domain.chat.repository.ChatRoomRepository;
import com.chunbaetour.domain.chat.type.ChatRoomStatus;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class ChatRoomOwnerTransferServiceTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ChatRoomService chatRoomService;

    private static final Long OWNER_ID = 1L;
    private static final Long NEW_OWNER_ID = 2L;
    private static final Long ROOM_ID = 100L;

    private ChatRoom room;

    @BeforeEach
    void setUp() {
        room = mock(ChatRoom.class);
        lenient().when(chatRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
        lenient().when(room.isOwnedBy(OWNER_ID)).thenReturn(true);
    }

    // 정상 위임 — promoteToOwner()/demoteFromOwner()/transferOwner() 호출 후 saveAndFlush
    @Test
    void transferOwner_success() {
        ChatRoomMember currentOwner = mock(ChatRoomMember.class);
        ChatRoomMember newOwner = mock(ChatRoomMember.class);
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, OWNER_ID))
                .willReturn(Optional.of(currentOwner));
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, NEW_OWNER_ID))
                .willReturn(Optional.of(newOwner));

        chatRoomService.transferOwner(OWNER_ID, ROOM_ID, NEW_OWNER_ID);

        verify(newOwner).promoteToOwner();
        verify(currentOwner).demoteFromOwner();
        verify(room).transferOwner(NEW_OWNER_ID);
        verify(chatRoomRepository).saveAndFlush(room);
        verify(eventPublisher).publishEvent(new ChatOwnerTransferredEvent(ROOM_ID, NEW_OWNER_ID));
    }

    // 존재하지 않는 채팅방 — CHAT_001
    @Test
    void transferOwner_nonExistentRoom_throws_CHAT_ROOM_NOT_FOUND() {
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> chatRoomService.transferOwner(OWNER_ID, ROOM_ID, NEW_OWNER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(this::extractErrorCode)
                .isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND);
    }

    // 방장이 아닌 사용자가 위임 시도 — CHAT_006
    @Test
    void transferOwner_notOwner_throws_CHAT_SETTING_FORBIDDEN() {
        given(room.isOwnedBy(OWNER_ID)).willReturn(false);

        assertThatThrownBy(() -> chatRoomService.transferOwner(OWNER_ID, ROOM_ID, NEW_OWNER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(this::extractErrorCode)
                .isEqualTo(ErrorCode.CHAT_SETTING_FORBIDDEN);
    }

    // 종료된 채팅방에서 위임 시도 — CHAT_013
    @Test
    void transferOwner_closedRoom_throws_CHAT_ROOM_CLOSED() {
        given(room.getStatus()).willReturn(ChatRoomStatus.CLOSED);

        assertThatThrownBy(() -> chatRoomService.transferOwner(OWNER_ID, ROOM_ID, NEW_OWNER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(this::extractErrorCode)
                .isEqualTo(ErrorCode.CHAT_ROOM_CLOSED);
    }

    // 현재 방장의 멤버 레코드 없음 — ChatRoom.ownerId와 ChatRoomMember 상태 불일치(서버 데이터 정합성 버그) — INTERNAL_SERVER_ERROR
    @Test
    void transferOwner_currentOwnerNotFound_throws_INTERNAL_SERVER_ERROR() {
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, OWNER_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> chatRoomService.transferOwner(OWNER_ID, ROOM_ID, NEW_OWNER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(this::extractErrorCode)
                .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);

        verify(chatRoomMemberRepository, never()).findByChatRoomIdAndUserId(ROOM_ID, NEW_OWNER_ID);
        verify(room, never()).transferOwner(any());
    }

    // 위임 대상이 채팅방 멤버가 아님 — CHAT_019
    @Test
    void transferOwner_targetNotJoined_throws_CHAT_OWNER_TRANSFER_INVALID_TARGET() {
        ChatRoomMember currentOwner = mock(ChatRoomMember.class);
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, OWNER_ID))
                .willReturn(Optional.of(currentOwner));
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, NEW_OWNER_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> chatRoomService.transferOwner(OWNER_ID, ROOM_ID, NEW_OWNER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(this::extractErrorCode)
                .isEqualTo(ErrorCode.CHAT_OWNER_TRANSFER_INVALID_TARGET);

        verify(currentOwner, never()).demoteFromOwner();
        verify(room, never()).transferOwner(any());
    }

    // 위임 대상이 MEMBER_ACTIVE가 아님(본인·강퇴·퇴장) — promoteToOwner() 내부에서 CHAT_019
    @Test
    void transferOwner_targetNotActiveMember_throws_CHAT_OWNER_TRANSFER_INVALID_TARGET() {
        ChatRoomMember currentOwner = mock(ChatRoomMember.class);
        ChatRoomMember newOwner = mock(ChatRoomMember.class);
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, OWNER_ID))
                .willReturn(Optional.of(currentOwner));
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, NEW_OWNER_ID))
                .willReturn(Optional.of(newOwner));
        doThrow(new BusinessException(ErrorCode.CHAT_OWNER_TRANSFER_INVALID_TARGET)).when(newOwner).promoteToOwner();

        assertThatThrownBy(() -> chatRoomService.transferOwner(OWNER_ID, ROOM_ID, NEW_OWNER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(this::extractErrorCode)
                .isEqualTo(ErrorCode.CHAT_OWNER_TRANSFER_INVALID_TARGET);

        verify(currentOwner, never()).demoteFromOwner();
        verify(room, never()).transferOwner(any());
    }

    // saveAndFlush 시 @Version 충돌 — CONCURRENT_UPDATE
    @Test
    void transferOwner_concurrentModification_throws_CONCURRENT_UPDATE() {
        ChatRoomMember currentOwner = mock(ChatRoomMember.class);
        ChatRoomMember newOwner = mock(ChatRoomMember.class);
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, OWNER_ID))
                .willReturn(Optional.of(currentOwner));
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, NEW_OWNER_ID))
                .willReturn(Optional.of(newOwner));
        willThrow(ObjectOptimisticLockingFailureException.class)
                .given(chatRoomRepository).saveAndFlush(room);

        assertThatThrownBy(() -> chatRoomService.transferOwner(OWNER_ID, ROOM_ID, NEW_OWNER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(this::extractErrorCode)
                .isEqualTo(ErrorCode.CONCURRENT_UPDATE);

        // saveAndFlush 실패로 예외 전파 — 알림 이벤트 발행 전 단계이므로 미호출
        verify(eventPublisher, never()).publishEvent(any());
    }

    private ErrorCode extractErrorCode(Throwable ex) {
        return ((BusinessException) ex).getErrorCode();
    }
}
