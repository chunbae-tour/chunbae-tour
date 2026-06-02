package com.chunbaetour.domain.cs.service;

import com.chunbaetour.domain.cs.dto.request.SupportSendMessageRequest;
import com.chunbaetour.domain.cs.dto.response.SupportMessageResponse;
import com.chunbaetour.domain.cs.entity.SupportMessage;
import com.chunbaetour.domain.cs.entity.SupportMessageType;
import com.chunbaetour.domain.cs.entity.SupportRoom;
import com.chunbaetour.domain.cs.entity.SupportRoomStatus;
import com.chunbaetour.domain.cs.entity.SupportSenderRole;
import com.chunbaetour.domain.cs.repository.SupportMessageRepository;
import com.chunbaetour.domain.cs.repository.SupportRoomRepository;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupportMessageService {

    private final SupportRoomRepository supportRoomRepository;
    private final SupportMessageRepository supportMessageRepository;
    private final SupportRedisPubSubService supportRedisPubSubService;

    // 메시지 전송 — 방 상태·발신 권한 검증, DB 저장, Redis 발행 (커밋 이후)
    @Transactional
    public void sendMessage(Long userId, Long supportRoomId, boolean isAdmin, SupportSendMessageRequest request) {
        SupportRoom room = supportRoomRepository.findById(supportRoomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUPPORT_ROOM_NOT_FOUND));

        // CLOSED 방 발신 차단
        if (room.getStatus() == SupportRoomStatus.CLOSED) {
            throw new BusinessException(ErrorCode.SUPPORT_ROOM_ALREADY_CLOSED);
        }

        SupportSenderRole senderRole;
        if (isAdmin) {
            // ADMIN은 배정된 방(IN_PROGRESS)에만 발신 가능
            if (room.getStatus() != SupportRoomStatus.IN_PROGRESS) {
                throw new BusinessException(ErrorCode.SUPPORT_ROOM_FORBIDDEN);
            }
            senderRole = SupportSenderRole.ADMIN;
        } else {
            // USER·MERCHANT는 본인 방에만 발신 가능
            if (!room.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.SUPPORT_ROOM_FORBIDDEN);
            }
            senderRole = SupportSenderRole.CUSTOMER;
        }

        SupportMessage message = SupportMessage.builder()
                .supportRoomId(supportRoomId)
                .senderId(userId)
                .senderRole(senderRole)
                .messageType(SupportMessageType.TEXT)
                .content(request.content())
                .fileUrl(null)
                .build();

        SupportMessage saved = supportMessageRepository.save(message);
        SupportMessageResponse response = SupportMessageResponse.from(saved);

        // DB 커밋 이후 발행 — 커밋 실패·롤백 시 유령 메시지 브로드캐스트 방지
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    supportRedisPubSubService.publish(supportRoomId, response);
                }
            });
        } else {
            supportRedisPubSubService.publish(supportRoomId, response);
        }
    }
}
