package com.chunbaetour.domain.cs.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.cs.dto.request.SupportRoomCreateRequest;
import com.chunbaetour.domain.cs.dto.response.SupportRoomResponse;
import com.chunbaetour.domain.cs.entity.SupportMessage;
import com.chunbaetour.domain.cs.entity.SupportMessageType;
import com.chunbaetour.domain.cs.entity.SupportRoom;
import com.chunbaetour.domain.cs.entity.SupportRoomStatus;
import java.util.List;
import com.chunbaetour.domain.cs.entity.SupportSenderRole;
import com.chunbaetour.domain.cs.repository.SupportMessageRepository;
import com.chunbaetour.domain.cs.repository.SupportRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SupportRoomService {

    private final SupportRoomRepository supportRoomRepository;
    private final SupportMessageRepository supportMessageRepository;

    // 상담방 생성 (USER·MERCHANT) — 활성 방 중복 차단(앱 레벨 선체크 + DB unique 제약 이중 방어)
    @Transactional
    public SupportRoomResponse createRoom(Long userId, SupportRoomCreateRequest request) {
        if (supportRoomRepository.existsByUserIdAndStatusIn(
                userId, List.of(SupportRoomStatus.WAITING, SupportRoomStatus.IN_PROGRESS))) {
            throw new BusinessException(ErrorCode.SUPPORT_ROOM_ALREADY_EXISTS);
        }

        SupportRoom room;
        try {
            room = supportRoomRepository.save(
                    SupportRoom.builder().userId(userId).build()
            );
        } catch (DataIntegrityViolationException e) {
            // uk_support_rooms_active_user 위반 — 동시 요청으로 앱 레벨 체크를 통과한 경우
            throw new BusinessException(ErrorCode.SUPPORT_ROOM_ALREADY_EXISTS);
        }

        if (request.initialMessage() != null && !request.initialMessage().isBlank()) {
            supportMessageRepository.save(
                    SupportMessage.builder()
                            .supportRoomId(room.getId())
                            .senderId(userId)
                            .senderRole(SupportSenderRole.CUSTOMER)
                            .messageType(SupportMessageType.TEXT)
                            .content(request.initialMessage())
                            .fileUrl(null)
                            .build()
            );
        }

        return SupportRoomResponse.from(room);
    }
}
