package com.chunbaetour.domain.cs.service;

import com.chunbaetour.domain.cs.dto.request.SupportSendMessageRequest;
import com.chunbaetour.domain.cs.dto.response.SupportMessageResponse;
import com.chunbaetour.domain.cs.entity.SupportMessage;
import com.chunbaetour.domain.cs.entity.SupportMessageType;
import com.chunbaetour.domain.cs.entity.SupportRoom;
import com.chunbaetour.domain.cs.entity.SupportRoomStatus;
import com.chunbaetour.domain.cs.entity.SupportSenderRole;
import com.chunbaetour.domain.cs.event.SupportMessageSentEvent;
import com.chunbaetour.domain.cs.repository.SupportMessageRepository;
import com.chunbaetour.domain.cs.repository.SupportRoomRepository;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.ratelimit.RateLimitDecision;
import com.chunbaetour.domain.common.ratelimit.RateLimitPolicy;
import com.chunbaetour.domain.common.ratelimit.RateLimiter;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupportMessageService {

    // 운영 보안 정책 — 상담 메시지 전송 20회/10초
    private static final RateLimitPolicy MESSAGE_RATE_LIMIT = new RateLimitPolicy(20, Duration.ofSeconds(10));

    private final SupportRoomRepository supportRoomRepository;
    private final SupportMessageRepository supportMessageRepository;
    private final SupportRedisPubSubService supportRedisPubSubService;
    private final RateLimiter rateLimiter;
    private final ApplicationEventPublisher applicationEventPublisher;

    // 메시지 전송 — rate limit 선검증, 방 상태·발신 권한 검증, 길이 검증, DB 저장, Redis 발행 (커밋 이후)
    @Transactional
    public void sendMessage(Long userId, Long supportRoomId, boolean isAdmin, SupportSendMessageRequest request) {
        // rate limit 선검증 — userId 단위 20회/10초
        RateLimitDecision decision = rateLimiter.tryConsume("ratelimit:support-message:" + userId, MESSAGE_RATE_LIMIT);
        if (!decision.allowed()) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }

        SupportRoom room = supportRoomRepository.findById(supportRoomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUPPORT_ROOM_NOT_FOUND));

        // CLOSED 방 발신 차단
        if (room.getStatus() == SupportRoomStatus.CLOSED) {
            throw new BusinessException(ErrorCode.SUPPORT_ROOM_ALREADY_CLOSED);
        }

        SupportSenderRole senderRole;
        if (isAdmin) {
            // 배정된 ADMIN만 발신 가능 — IN_PROGRESS 상태 + adminId 일치
            if (room.getStatus() != SupportRoomStatus.IN_PROGRESS) {
                throw new BusinessException(ErrorCode.SUPPORT_ROOM_FORBIDDEN);
            }
            if (!userId.equals(room.getAdminId())) {
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

        // STOMP @Payload는 Bean Validation 미적용 — 서비스에서 명시적 검증
        if (request.content() == null || request.content().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (request.content().length() > 1000) {
            throw new BusinessException(ErrorCode.MESSAGE_TOO_LONG);
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

        // 알림 이벤트 발행 — ADMIN 발신 시 방 소유자, USER/MERCHANT 발신 시 배정 ADMIN(null이면 미발송)
        Long recipientId = isAdmin ? room.getUserId() : room.getAdminId();
        if (recipientId != null) {
            applicationEventPublisher.publishEvent(new SupportMessageSentEvent(supportRoomId, recipientId));
        }

        // DB 커밋 이후 발행 — 커밋 실패·롤백 시 유령 메시지 브로드캐스트 방지
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    supportRedisPubSubService.publish(supportRoomId, response);
                }
            });
        } else {
            // 트랜잭션 없는 컨텍스트(단위 테스트 등)에서만 진입 — 프로덕션에서 도달하지 않음
            supportRedisPubSubService.publish(supportRoomId, response);
        }
    }
}
