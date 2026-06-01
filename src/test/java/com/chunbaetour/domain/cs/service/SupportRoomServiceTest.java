package com.chunbaetour.domain.cs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.cs.dto.request.SupportRoomCloseRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.cs.dto.request.SupportRoomCreateRequest;
import com.chunbaetour.domain.cs.dto.response.SupportMessageResponse;
import com.chunbaetour.domain.cs.dto.response.SupportRoomResponse;
import com.chunbaetour.domain.cs.entity.SupportMessage;
import com.chunbaetour.domain.cs.entity.SupportMessageType;
import com.chunbaetour.domain.cs.entity.SupportRoom;
import com.chunbaetour.domain.cs.entity.SupportRoomStatus;
import com.chunbaetour.domain.cs.entity.SupportSenderRole;
import com.chunbaetour.domain.cs.repository.SupportMessageRepository;
import com.chunbaetour.domain.cs.repository.SupportRoomRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class SupportRoomServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC);

    @InjectMocks private SupportRoomService supportRoomService;
    @Mock private Clock clock;
    @Mock private SupportRoomRepository supportRoomRepository;
    @Mock private SupportMessageRepository supportMessageRepository;
    @Mock private AccountRepository accountRepository;

    // ===== createRoom =====

    // WAITING 상담방 이미 있으면 CS_004 예외
    @Test
    void createRoom_whenWaitingRoomExists_throwsAlreadyExists() {
        given(supportRoomRepository.existsByUserIdAndStatusIn(eq(1L), any()))
                .willReturn(true);

        assertThatThrownBy(() -> supportRoomService.createRoom(1L, new SupportRoomCreateRequest(null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.SUPPORT_ROOM_ALREADY_EXISTS));
        verify(supportRoomRepository, never()).save(any(SupportRoom.class));
    }

    // initialMessage 없으면 SupportMessage 저장 안 함
    @Test
    void createRoom_withoutMessage_doesNotSaveMessage() {
        SupportRoom room = buildRoom(1L);
        given(supportRoomRepository.existsByUserIdAndStatusIn(eq(1L), any())).willReturn(false);
        given(supportRoomRepository.save(any(SupportRoom.class))).willReturn(room);

        supportRoomService.createRoom(1L, new SupportRoomCreateRequest(null));

        verify(supportMessageRepository, never()).save(any(SupportMessage.class));
    }

    // initialMessage blank이면 SupportMessage 저장 안 함
    @Test
    void createRoom_withBlankMessage_doesNotSaveMessage() {
        SupportRoom room = buildRoom(1L);
        given(supportRoomRepository.existsByUserIdAndStatusIn(eq(1L), any())).willReturn(false);
        given(supportRoomRepository.save(any(SupportRoom.class))).willReturn(room);

        supportRoomService.createRoom(1L, new SupportRoomCreateRequest("   "));

        verify(supportMessageRepository, never()).save(any(SupportMessage.class));
    }

    // initialMessage 제공 시 SupportMessage 저장 — senderId/senderRole/content/messageType 검증
    @Test
    void createRoom_withMessage_savesMessageWithCorrectFields() {
        SupportRoom room = buildRoom(1L);
        given(supportRoomRepository.existsByUserIdAndStatusIn(eq(1L), any())).willReturn(false);
        given(supportRoomRepository.save(any(SupportRoom.class))).willReturn(room);

        supportRoomService.createRoom(1L, new SupportRoomCreateRequest("결제가 안 됩니다."));

        verify(supportMessageRepository).save(argThat(msg ->
                msg.getSupportRoomId().equals(1L) &&
                msg.getSenderId().equals(1L) &&
                msg.getSenderRole() == SupportSenderRole.CUSTOMER &&
                msg.getMessageType() == SupportMessageType.TEXT &&
                "결제가 안 됩니다.".equals(msg.getContent())
        ));
    }

    // 생성된 상담방 status = WAITING
    @Test
    void createRoom_statusIsWaiting() {
        SupportRoom room = buildRoom(1L);
        given(supportRoomRepository.existsByUserIdAndStatusIn(eq(1L), any())).willReturn(false);
        given(supportRoomRepository.save(any(SupportRoom.class))).willReturn(room);

        SupportRoomResponse result = supportRoomService.createRoom(1L, new SupportRoomCreateRequest(null));

        assertThat(result.status()).isEqualTo(SupportRoomStatus.WAITING);
    }

    // ===== closeRoom =====

    // 정상 종료 → CLOSED + closedAt 설정
    @Test
    void closeRoom_success_returnsClosedRoom() {
        SupportRoom room = buildRoom(1L);
        given(supportRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(clock.instant()).willReturn(FIXED_CLOCK.instant());
        given(clock.getZone()).willReturn(FIXED_CLOCK.getZone());

        SupportRoomResponse result = supportRoomService.closeRoom(1L, new SupportRoomCloseRequest("해결 완료"));

        assertThat(result.status()).isEqualTo(SupportRoomStatus.CLOSED);
        assertThat(result.summary()).isEqualTo("해결 완료");
        assertThat(result.closedAt()).isNotNull();
    }

    // 존재하지 않는 방 → CS_001
    @Test
    void closeRoom_whenNotFound_throwsNotFound() {
        given(supportRoomRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> supportRoomService.closeRoom(999L, new SupportRoomCloseRequest(null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.SUPPORT_ROOM_NOT_FOUND));
    }

    // ===== getMessages =====

    // 본인 방 → 메시지 반환
    @Test
    void getMessages_whenOwner_returnsMessages() {
        SupportRoom room = buildRoom(1L);
        SupportMessage msg = buildMessage(1L, 1L);
        given(supportRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(supportMessageRepository.findMessagesWithCursor(eq(1L), any(), any(PageRequest.class)))
                .willReturn(List.of(msg));

        CursorPageResponse<SupportMessageResponse> result = supportRoomService.getMessages(1L, 1L, null, 20);

        assertThat(result.content()).hasSize(1);
    }

    // 타인 방 → CS_003
    @Test
    void getMessages_whenNotOwner_throwsForbidden() {
        SupportRoom room = buildRoom(1L); // userId=1
        given(supportRoomRepository.findById(1L)).willReturn(Optional.of(room));

        assertThatThrownBy(() -> supportRoomService.getMessages(99L, 1L, null, 20))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.SUPPORT_ROOM_FORBIDDEN));
    }

    // 존재하지 않는 방 → CS_001
    @Test
    void getMessages_whenRoomNotFound_throwsNotFound() {
        given(supportRoomRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> supportRoomService.getMessages(1L, 999L, null, 20))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.SUPPORT_ROOM_NOT_FOUND));
    }

    // ===== getMyRooms =====

    // hasNext=true when result > size
    @Test
    void getMyRooms_hasNextTrue_whenResultExceedsSize() {
        given(supportRoomRepository.findMyRoomsWithCursor(eq(1L), any(), any(), any(PageRequest.class)))
                .willReturn(List.of(buildRoom(1L), buildRoom(2L), buildRoom(3L)));

        CursorPageResponse<SupportRoomResponse> result = supportRoomService.getMyRooms(1L, null, 2, null);

        assertThat(result.hasNext()).isTrue();
        assertThat(result.content()).hasSize(2);
    }

    private SupportRoom buildRoom(Long id) {
        SupportRoom room = SupportRoom.builder().userId(1L).build();
        try {
            var field = SupportRoom.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(room, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return room;
    }

    private SupportMessage buildMessage(Long id, Long roomId) {
        SupportMessage msg = SupportMessage.builder()
                .supportRoomId(roomId)
                .senderId(1L)
                .senderRole(SupportSenderRole.CUSTOMER)
                .messageType(SupportMessageType.TEXT)
                .content("테스트 메시지")
                .fileUrl(null)
                .build();
        try {
            var field = SupportMessage.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(msg, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return msg;
    }
}
