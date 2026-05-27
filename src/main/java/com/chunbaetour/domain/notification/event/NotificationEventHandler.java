package com.chunbaetour.domain.notification.event;

import com.chunbaetour.domain.chat.event.ChatMemberKickedEvent;
import com.chunbaetour.domain.chat.event.JoinRequestApprovedEvent;
import com.chunbaetour.domain.chat.event.JoinRequestCreatedEvent;
import com.chunbaetour.domain.chat.event.JoinRequestRejectedEvent;
import com.chunbaetour.domain.notification.service.NotificationService;
import com.chunbaetour.domain.notification.type.NotificationReferenceType;
import com.chunbaetour.domain.notification.type.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class NotificationEventHandler {

    private final NotificationService notificationService;

    // 참여 신청 생성 — 방장에게 CHAT_JOIN_REQUEST 알림, 원본 트랜잭션 커밋 후 새 트랜잭션에서 저장
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleJoinRequestCreated(JoinRequestCreatedEvent event) {
        notificationService.createNotification(
                event.ownerUserId(),
                NotificationType.CHAT_JOIN_REQUEST,
                "참여 신청 도착",
                "채팅방 참여 신청이 도착했어요.",
                NotificationReferenceType.JOIN_REQUEST,
                event.joinRequestId());
    }

    // 참여 신청 수락 — 신청자에게 CHAT_JOIN_APPROVED 알림
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleJoinRequestApproved(JoinRequestApprovedEvent event) {
        notificationService.createNotification(
                event.applicantUserId(),
                NotificationType.CHAT_JOIN_APPROVED,
                "참여 신청 승인",
                "참여 신청이 승인됐어요.",
                NotificationReferenceType.JOIN_REQUEST,
                event.joinRequestId());
    }

    // 참여 신청 거절 — 신청자에게 CHAT_JOIN_REJECTED 알림
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleJoinRequestRejected(JoinRequestRejectedEvent event) {
        notificationService.createNotification(
                event.applicantUserId(),
                NotificationType.CHAT_JOIN_REJECTED,
                "참여 신청 거절",
                "참여 신청이 거절됐어요.",
                NotificationReferenceType.JOIN_REQUEST,
                event.joinRequestId());
    }

    // 멤버 강퇴 — 강퇴 대상에게 CHAT_MEMBER_KICKED 알림
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleChatMemberKicked(ChatMemberKickedEvent event) {
        notificationService.createNotification(
                event.kickedUserId(),
                NotificationType.CHAT_MEMBER_KICKED,
                "채팅방 강퇴",
                "채팅방에서 강퇴됐어요.",
                NotificationReferenceType.CHAT_ROOM,
                event.chatRoomId());
    }
}
