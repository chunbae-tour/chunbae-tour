package com.chunbaetour.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.chat.entity.ChatRoom;
import com.chunbaetour.domain.chat.entity.ChatRoomMember;
import com.chunbaetour.domain.chat.event.ChatMemberKickedEvent;
import com.chunbaetour.domain.chat.repository.ChatRoomMemberRepository;
import com.chunbaetour.domain.chat.repository.ChatRoomRepository;
import com.chunbaetour.domain.chat.type.ChatRoomStatus;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import org.springframework.context.ApplicationEventPublisher;
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
class ChatRoomKickServiceTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ChatRoomService chatRoomService;

    private static final Long OWNER_ID = 1L;
    private static final Long TARGET_ID = 2L;
    private static final Long ROOM_ID = 100L;

    private ChatRoom room;

    @BeforeEach
    void setUp() {
        room = mock(ChatRoom.class);
        // lenient: kickMember_nonExistentRoom에서 findById를 재정의하므로 기본 스텁이 미사용 경고 없이 동작해야 함
        lenient().when(chatRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
        lenient().when(room.isOwnedBy(OWNER_ID)).thenReturn(true);
    }

    // 정상 강퇴 — kick() → saveAndFlush(target) → decrementMembers() → saveAndFlush(room) 순서 보장, 이벤트 발행 확인
    @Test
    void kickMember_success() {
        ChatRoomMember target = mock(ChatRoomMember.class);
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, TARGET_ID))
                .willReturn(Optional.of(target));

        chatRoomService.kickMember(OWNER_ID, ROOM_ID, TARGET_ID);

        InOrder inOrder = inOrder(target, chatRoomMemberRepository, room, chatRoomRepository);
        inOrder.verify(target).kick();
        inOrder.verify(chatRoomMemberRepository).saveAndFlush(target);
        inOrder.verify(room).decrementMembers();
        inOrder.verify(chatRoomRepository).saveAndFlush(room);
        verify(eventPublisher).publishEvent(new ChatMemberKickedEvent(ROOM_ID, TARGET_ID));
    }

    // 존재하지 않는 채팅방 — CHAT_001
    @Test
    void kickMember_nonExistentRoom_throws_CHAT_ROOM_NOT_FOUND() {
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> chatRoomService.kickMember(OWNER_ID, ROOM_ID, TARGET_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(this::extractErrorCode)
                .isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND);
    }

    // 방장이 아닌 사용자가 강퇴 시도 — CHAT_006
    @Test
    void kickMember_notOwner_throws_CHAT_SETTING_FORBIDDEN() {
        given(room.isOwnedBy(OWNER_ID)).willReturn(false);

        assertThatThrownBy(() -> chatRoomService.kickMember(OWNER_ID, ROOM_ID, TARGET_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(this::extractErrorCode)
                .isEqualTo(ErrorCode.CHAT_SETTING_FORBIDDEN);
    }

    // 채팅방에 참여하지 않은 대상 강퇴 시도 — CHAT_005
    @Test
    void kickMember_targetNotJoined_throws_CHAT_NOT_JOINED() {
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, TARGET_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> chatRoomService.kickMember(OWNER_ID, ROOM_ID, TARGET_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(this::extractErrorCode)
                .isEqualTo(ErrorCode.CHAT_NOT_JOINED);
    }

    // 방장 자기 강퇴 시도 — kick() 내부에서 OWNER_ACTIVE 상태면 CHAT_017, decrementMembers() 미호출
    @Test
    void kickMember_kickOwner_throws_CHAT_OWNER_CANNOT_BE_KICKED() {
        ChatRoomMember ownerMember = mock(ChatRoomMember.class);
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, OWNER_ID))
                .willReturn(Optional.of(ownerMember));
        doThrow(new BusinessException(ErrorCode.CHAT_OWNER_CANNOT_BE_KICKED)).when(ownerMember).kick();

        assertThatThrownBy(() -> chatRoomService.kickMember(OWNER_ID, ROOM_ID, OWNER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(this::extractErrorCode)
                .isEqualTo(ErrorCode.CHAT_OWNER_CANNOT_BE_KICKED);

        verify(room, never()).decrementMembers();
    }

    // 이미 강퇴된(MEMBER_KICKED) 멤버 재강퇴 시도 — kick() 내부 MEMBER_KICKED 분기 → CHAT_016, decrementMembers() 미호출
    @Test
    void kickMember_alreadyKicked_throws_CHAT_MEMBER_ALREADY_INACTIVE() {
        ChatRoomMember target = mock(ChatRoomMember.class);
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, TARGET_ID))
                .willReturn(Optional.of(target));
        doThrow(new BusinessException(ErrorCode.CHAT_MEMBER_ALREADY_INACTIVE)).when(target).kick();

        assertThatThrownBy(() -> chatRoomService.kickMember(OWNER_ID, ROOM_ID, TARGET_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(this::extractErrorCode)
                .isEqualTo(ErrorCode.CHAT_MEMBER_ALREADY_INACTIVE);

        verify(room, never()).decrementMembers();
    }

    // 이미 퇴장한(MEMBER_LEFT) 멤버 강퇴 시도 — kick() 내부 MEMBER_LEFT 분기 → CHAT_016, decrementMembers() 미호출
    @Test
    void kickMember_alreadyLeft_throws_CHAT_MEMBER_ALREADY_INACTIVE() {
        ChatRoomMember target = mock(ChatRoomMember.class);
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, TARGET_ID))
                .willReturn(Optional.of(target));
        doThrow(new BusinessException(ErrorCode.CHAT_MEMBER_ALREADY_INACTIVE)).when(target).kick();

        assertThatThrownBy(() -> chatRoomService.kickMember(OWNER_ID, ROOM_ID, TARGET_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(this::extractErrorCode)
                .isEqualTo(ErrorCode.CHAT_MEMBER_ALREADY_INACTIVE);

        verify(room, never()).decrementMembers();
    }

    // 종료된 채팅방에서 강퇴 시도 — CHAT_013, decrementMembers() 미호출
    @Test
    void kickMember_closedRoom_throws_CHAT_ROOM_CLOSED() {
        given(room.getStatus()).willReturn(ChatRoomStatus.CLOSED);

        assertThatThrownBy(() -> chatRoomService.kickMember(OWNER_ID, ROOM_ID, TARGET_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(this::extractErrorCode)
                .isEqualTo(ErrorCode.CHAT_ROOM_CLOSED);

        verify(room, never()).decrementMembers();
    }

    // saveAndFlush 시 @Version 충돌 — 동시 kick/leave/approve가 먼저 currentMembers 변경한 경우 CONCURRENT_UPDATE
    @Test
    void kickMember_concurrentModification_throws_CONCURRENT_UPDATE() {
        ChatRoomMember target = mock(ChatRoomMember.class);
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(ROOM_ID, TARGET_ID))
                .willReturn(Optional.of(target));
        willThrow(ObjectOptimisticLockingFailureException.class)
                .given(chatRoomRepository).saveAndFlush(room);

        assertThatThrownBy(() -> chatRoomService.kickMember(OWNER_ID, ROOM_ID, TARGET_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(this::extractErrorCode)
                .isEqualTo(ErrorCode.CONCURRENT_UPDATE);

        // saveAndFlush(room) 충돌 전까지 kick()/saveAndFlush(target)/decrementMembers()는 이미 실행된 상태임을 명시
        verify(target).kick();
        verify(chatRoomMemberRepository).saveAndFlush(target);
        verify(room).decrementMembers();
        verify(chatRoomRepository).saveAndFlush(room);
    }

    private ErrorCode extractErrorCode(Throwable ex) {
        return ((BusinessException) ex).getErrorCode();
    }
}
