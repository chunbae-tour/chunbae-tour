package com.chunbaetour.domain.cs.service;

import com.chunbaetour.domain.cs.dto.request.SupportRoomCreateRequest;
import com.chunbaetour.domain.cs.dto.response.SupportRoomResponse;
import com.chunbaetour.domain.cs.entity.SupportMessage;
import com.chunbaetour.domain.cs.entity.SupportMessageType;
import com.chunbaetour.domain.cs.entity.SupportRoom;
import com.chunbaetour.domain.cs.entity.SupportSenderRole;
import com.chunbaetour.domain.cs.repository.SupportMessageRepository;
import com.chunbaetour.domain.cs.repository.SupportRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SupportRoomService {

    private final SupportRoomRepository supportRoomRepository;
    private final SupportMessageRepository supportMessageRepository;

    // USER 상담방 생성 — initialMessage 제공 시 첫 메시지(TEXT) 함께 저장
    @Transactional
    public SupportRoomResponse createRoom(Long userId, SupportRoomCreateRequest request) {
        SupportRoom room = supportRoomRepository.save(
                SupportRoom.builder().userId(userId).build()
        );

        if (request.initialMessage() != null && !request.initialMessage().isBlank()) {
            supportMessageRepository.save(
                    SupportMessage.builder()
                            .supportRoomId(room.getId())
                            .senderId(userId)
                            .senderRole(SupportSenderRole.USER)
                            .messageType(SupportMessageType.TEXT)
                            .content(request.initialMessage())
                            .fileUrl(null)
                            .build()
            );
        }

        return SupportRoomResponse.from(room);
    }
}
