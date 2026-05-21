package com.chunbaetour.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.chat.entity.ChatRoom;
import com.chunbaetour.domain.chat.repository.ChatRoomMemberRepository;
import com.chunbaetour.domain.chat.repository.ChatRoomRepository;
import com.chunbaetour.domain.chat.type.ChatRoomStatus;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class ChatRoomCloseServiceTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepository;

    @InjectMocks
    private ChatRoomService chatRoomService;

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long ROOM_ID = 100L;

    @Test
    void closeRoom_owner_succeeds() {
        // 방장만 채팅방 종료 가능 — close() 호출로 상태가 CLOSED로 전이됨
        ChatRoom room = mock(ChatRoom.class);
        given(room.getOwnerId()).willReturn(OWNER_ID);
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));

        chatRoomService.closeRoom(OWNER_ID, ROOM_ID);

        verify(room).close();
        // saveAndFlush로 낙관적 잠금 실패를 메서드 내부에서 처리하므로 반드시 호출되어야 함
        verify(chatRoomRepository).saveAndFlush(room);
    }

    @Test
    void closeRoom_non_owner_throws_CHAT_SETTING_FORBIDDEN() {
        // 방장이 아닌 멤버는 채팅방 종료 불가 — 설정 변경 권한은 방장 전용
        ChatRoom room = mock(ChatRoom.class);
        given(room.getOwnerId()).willReturn(OWNER_ID);
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));

        assertThatThrownBy(() -> chatRoomService.closeRoom(OTHER_USER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_SETTING_FORBIDDEN);
    }

    @Test
    void closeRoom_nonExistentRoom_throws_CHAT_ROOM_NOT_FOUND() {
        // 존재하지 않는 방 종료 시도 — findById가 empty를 반환하면 CHAT_001 예외 발생
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> chatRoomService.closeRoom(OWNER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND);
    }

    @Test
    void closeRoom_already_closed_throws_CHAT_ROOM_CLOSED() {
        // 이미 종료된 방에 close() 재요청 시 ChatRoom 도메인 메서드가 CHAT_013 예외 발생
        ChatRoom room = mock(ChatRoom.class);
        given(room.getOwnerId()).willReturn(OWNER_ID);
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
        // close() 호출 시 ChatRoom 도메인이 직접 예외를 던지므로 서비스 레벨에서 전파됨
        doThrow(new BusinessException(ErrorCode.CHAT_ROOM_CLOSED)).when(room).close();

        assertThatThrownBy(() -> chatRoomService.closeRoom(OWNER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_ROOM_CLOSED);
    }

    @Test
    void closeRoom_concurrent_close_throws_CHAT_ROOM_CLOSED() {
        // 동시 close() 경합 — saveAndFlush 시 낙관적 잠금 실패, 재조회 결과 CLOSED이면 CHAT_013
        ChatRoom room = mock(ChatRoom.class);
        ChatRoom refreshed = mock(ChatRoom.class);
        given(room.getOwnerId()).willReturn(OWNER_ID);
        given(chatRoomRepository.findById(ROOM_ID))
                .willReturn(Optional.of(room))
                .willReturn(Optional.of(refreshed));
        given(refreshed.getStatus()).willReturn(ChatRoomStatus.CLOSED);
        doThrow(new ObjectOptimisticLockingFailureException(ChatRoom.class, ROOM_ID))
                .when(chatRoomRepository).saveAndFlush(room);

        assertThatThrownBy(() -> chatRoomService.closeRoom(OWNER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_ROOM_CLOSED);
    }

    @Test
    void closeRoom_concurrent_other_modification_throws_CONCURRENT_UPDATE() {
        // 동시 다른 필드 수정(멤버 수 등) 경합 — 재조회 결과 OPEN이면 CONCURRENT_UPDATE
        ChatRoom room = mock(ChatRoom.class);
        ChatRoom refreshed = mock(ChatRoom.class);
        given(room.getOwnerId()).willReturn(OWNER_ID);
        given(chatRoomRepository.findById(ROOM_ID))
                .willReturn(Optional.of(room))
                .willReturn(Optional.of(refreshed));
        given(refreshed.getStatus()).willReturn(ChatRoomStatus.OPEN);
        doThrow(new ObjectOptimisticLockingFailureException(ChatRoom.class, ROOM_ID))
                .when(chatRoomRepository).saveAndFlush(room);

        assertThatThrownBy(() -> chatRoomService.closeRoom(OWNER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONCURRENT_UPDATE);
    }
}
