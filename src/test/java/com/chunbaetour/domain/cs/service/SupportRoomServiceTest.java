package com.chunbaetour.domain.cs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.cs.dto.request.SupportRoomCreateRequest;
import com.chunbaetour.domain.cs.dto.response.SupportRoomResponse;
import com.chunbaetour.domain.cs.entity.SupportMessage;
import com.chunbaetour.domain.cs.entity.SupportMessageType;
import com.chunbaetour.domain.cs.entity.SupportRoom;
import com.chunbaetour.domain.cs.entity.SupportRoomStatus;
import com.chunbaetour.domain.cs.entity.SupportSenderRole;
import com.chunbaetour.domain.cs.repository.SupportMessageRepository;
import com.chunbaetour.domain.cs.repository.SupportRoomRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SupportRoomServiceTest {

    @InjectMocks private SupportRoomService supportRoomService;
    @Mock private SupportRoomRepository supportRoomRepository;
    @Mock private SupportMessageRepository supportMessageRepository;

    // ===== createRoom =====

    // initialMessage 없으면 SupportMessage 저장 안 함
    @Test
    void createRoom_withoutMessage_doesNotSaveMessage() {
        SupportRoom room = buildRoom(1L);
        given(supportRoomRepository.save(any(SupportRoom.class))).willReturn(room);

        supportRoomService.createRoom(1L, new SupportRoomCreateRequest(null));

        verify(supportMessageRepository, never()).save(any(SupportMessage.class));
    }

    // initialMessage blank이면 SupportMessage 저장 안 함
    @Test
    void createRoom_withBlankMessage_doesNotSaveMessage() {
        SupportRoom room = buildRoom(1L);
        given(supportRoomRepository.save(any(SupportRoom.class))).willReturn(room);

        supportRoomService.createRoom(1L, new SupportRoomCreateRequest("   "));

        verify(supportMessageRepository, never()).save(any(SupportMessage.class));
    }

    // initialMessage 제공 시 SupportMessage 저장 — senderId/senderRole/content/messageType 검증
    @Test
    void createRoom_withMessage_savesMessageWithCorrectFields() {
        SupportRoom room = buildRoom(1L);
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
        given(supportRoomRepository.save(any(SupportRoom.class))).willReturn(room);

        SupportRoomResponse result = supportRoomService.createRoom(1L, new SupportRoomCreateRequest(null));

        assertThat(result.status()).isEqualTo(SupportRoomStatus.WAITING);
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
}
