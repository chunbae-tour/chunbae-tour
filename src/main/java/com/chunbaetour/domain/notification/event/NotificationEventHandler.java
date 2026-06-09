package com.chunbaetour.domain.notification.event;

import com.chunbaetour.domain.chat.event.ChatMemberKickedEvent;
import com.chunbaetour.domain.chat.event.ChatMessageSentEvent;
import com.chunbaetour.domain.chat.event.JoinRequestApprovedEvent;
import com.chunbaetour.domain.chat.event.JoinRequestCreatedEvent;
import com.chunbaetour.domain.chat.event.JoinRequestRejectedEvent;
import com.chunbaetour.domain.cs.event.SupportMessageSentEvent;
import com.chunbaetour.domain.cs.event.SupportRoomClosedEvent;
import com.chunbaetour.domain.notification.dto.response.NotificationResponse;
import com.chunbaetour.domain.notification.entity.Notification;
import com.chunbaetour.domain.notification.service.NotificationRedisPubSubService;
import com.chunbaetour.domain.notification.service.NotificationService;
import com.chunbaetour.domain.notification.type.NotificationReferenceType;
import com.chunbaetour.domain.notification.type.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventHandler {

    private final NotificationService notificationService;
    private final NotificationRedisPubSubService notificationRedisPubSubService;
    private final SimpUserRegistry simpUserRegistry;

    // 참여 신청 생성 — 방장에게 CHAT_JOIN_REQUEST 알림, 원본 트랜잭션 커밋 후 새 트랜잭션에서 저장
    // REQUIRES_NEW 트랜잭션 실패 시 원본 비즈니스 흐름 영향 없음 — log.error로 silent loss 추적
    // Push는 REQUIRES_NEW afterCommit 훅에서 전송 — 커밋 전 유령 알림/미노출 방지
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleJoinRequestCreated(JoinRequestCreatedEvent event) {
        try {
            Notification notification = notificationService.createNotification(
                    event.ownerUserId(),
                    NotificationType.CHAT_JOIN_REQUEST,
                    "참여 신청 도착",
                    "채팅방 참여 신청이 도착했어요.",
                    NotificationReferenceType.JOIN_REQUEST,
                    event.joinRequestId());
            pushNotification(notification);
        } catch (RuntimeException e) {
            log.error("알림 저장 실패 — chatRoomId={}, joinRequestId={}, ownerUserId={}",
                    event.chatRoomId(), event.joinRequestId(), event.ownerUserId(), e);
        }
    }

    // 참여 신청 수락 — 신청자에게 CHAT_JOIN_APPROVED 알림, referenceType=CHAT_ROOM (채팅방 이동용)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleJoinRequestApproved(JoinRequestApprovedEvent event) {
        try {
            Notification notification = notificationService.createNotification(
                    event.applicantUserId(),
                    NotificationType.CHAT_JOIN_APPROVED,
                    "참여 신청 승인",
                    "참여 신청이 승인됐어요.",
                    NotificationReferenceType.CHAT_ROOM,
                    event.chatRoomId());
            pushNotification(notification);
        } catch (RuntimeException e) {
            log.error("알림 저장 실패 — chatRoomId={}, joinRequestId={}, applicantUserId={}",
                    event.chatRoomId(), event.joinRequestId(), event.applicantUserId(), e);
        }
    }

    // 참여 신청 거절 — 신청자에게 CHAT_JOIN_REJECTED 알림, referenceType=CHAT_ROOM (거절된 방 확인용)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleJoinRequestRejected(JoinRequestRejectedEvent event) {
        try {
            Notification notification = notificationService.createNotification(
                    event.applicantUserId(),
                    NotificationType.CHAT_JOIN_REJECTED,
                    "참여 신청 거절",
                    "참여 신청이 거절됐어요.",
                    NotificationReferenceType.CHAT_ROOM,
                    event.chatRoomId());
            pushNotification(notification);
        } catch (RuntimeException e) {
            log.error("알림 저장 실패 — chatRoomId={}, joinRequestId={}, applicantUserId={}",
                    event.chatRoomId(), event.joinRequestId(), event.applicantUserId(), e);
        }
    }

    // 멤버 강퇴 — 강퇴 대상에게 CHAT_MEMBER_KICKED 알림
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleChatMemberKicked(ChatMemberKickedEvent event) {
        try {
            Notification notification = notificationService.createNotification(
                    event.kickedUserId(),
                    NotificationType.CHAT_MEMBER_KICKED,
                    "채팅방 강퇴",
                    "채팅방에서 강퇴됐어요.",
                    NotificationReferenceType.CHAT_ROOM,
                    event.chatRoomId());
            pushNotification(notification);
        } catch (RuntimeException e) {
            log.error("알림 저장 실패 — chatRoomId={}, kickedUserId={}",
                    event.chatRoomId(), event.kickedUserId(), e);
        }
    }

    // 채팅 메시지 전송 — 미연결(오프라인) 멤버에게만 CHAT_MESSAGE 알림, 한 명 실패해도 나머지 계속 처리
    // 오프라인 판단: SimpUserRegistry.getUser(userId) != null → WebSocket 연결 중 → 알림 skip (정책 안B, 09_정책_결정_기록.md)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleChatMessageSent(ChatMessageSentEvent event) {
        for (Long recipientUserId : event.recipientUserIds()) {
            if (simpUserRegistry.getUser(String.valueOf(recipientUserId)) != null) {
                continue;
            }
            try {
                Notification notification = notificationService.createNotification(
                        recipientUserId,
                        NotificationType.CHAT_MESSAGE,
                        "새 채팅 메시지",
                        "채팅방에 새 메시지가 도착했어요.",
                        NotificationReferenceType.CHAT_ROOM,
                        event.chatRoomId());
                pushNotification(notification);
            } catch (RuntimeException e) {
                log.error("알림 저장 실패 — chatRoomId={}, senderId={}, recipientUserId={}",
                        event.chatRoomId(), event.senderId(), recipientUserId, e);
            }
        }
    }

    // Redis Pub/Sub으로 Push — REQUIRES_NEW 커밋 후 전송해 유령 알림/미노출 방지
    private void pushNotification(Notification notification) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            doPush(notification);
                        }
                    });
            return;
        }
        doPush(notification);
    }

    // 상담 메시지 전송 — 수신 대상에게 SUPPORT_MESSAGE 알림
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleSupportMessageSent(SupportMessageSentEvent event) {
        Notification notification;
        try {
            notification = notificationService.createNotification(
                    event.recipientUserId(),
                    NotificationType.SUPPORT_MESSAGE,
                    "고객센터 메시지 도착",
                    "고객센터 상담 메시지가 도착했어요.",
                    NotificationReferenceType.SUPPORT_ROOM,
                    event.supportRoomId());
        } catch (RuntimeException e) {
            log.error("알림 저장 실패 — supportRoomId={}, recipientUserId={}",
                    event.supportRoomId(), event.recipientUserId(), e);
            return;
        }
        try {
            pushNotification(notification);
        } catch (RuntimeException e) {
            log.warn("알림 Push 등록 실패 — 알림 저장 완료, Push 미발송. supportRoomId={}",
                    event.supportRoomId(), e);
        }
    }

    // 상담 종료 — 방 소유자에게 SUPPORT_ROOM_CLOSED 알림
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleSupportRoomClosed(SupportRoomClosedEvent event) {
        Notification notification;
        try {
            notification = notificationService.createNotification(
                    event.userId(),
                    NotificationType.SUPPORT_ROOM_CLOSED,
                    "상담 종료",
                    "고객센터 상담이 종료됐어요.",
                    NotificationReferenceType.SUPPORT_ROOM,
                    event.supportRoomId());
        } catch (RuntimeException e) {
            log.error("알림 저장 실패 — supportRoomId={}, userId={}",
                    event.supportRoomId(), event.userId(), e);
            return;
        }
        try {
            pushNotification(notification);
        } catch (RuntimeException e) {
            log.warn("알림 Push 등록 실패 — 알림 저장 완료, Push 미발송. supportRoomId={}",
                    event.supportRoomId(), e);
        }
    }

    // 전송 실패가 알림 저장 롤백에 영향 없도록 별도 try-catch
    private void doPush(Notification notification) {
        try {
            notificationRedisPubSubService.publish(
                    notification.getUserId(),
                    NotificationResponse.from(notification));
        } catch (Exception e) {
            log.warn("WebSocket 알림 Push 실패 — userId={}, notificationId={}",
                    notification.getUserId(), notification.getId(), e);
        }
    }
}
