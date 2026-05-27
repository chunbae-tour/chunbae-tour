package com.chunbaetour.domain.notification.event;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.chat.event.ChatMemberKickedEvent;
import com.chunbaetour.domain.chat.event.JoinRequestApprovedEvent;
import com.chunbaetour.domain.chat.event.JoinRequestCreatedEvent;
import com.chunbaetour.domain.chat.event.JoinRequestRejectedEvent;
import com.chunbaetour.domain.notification.service.NotificationService;
import com.chunbaetour.domain.notification.type.NotificationReferenceType;
import com.chunbaetour.domain.notification.type.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationEventHandlerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationEventHandler handler;

    private static final Long CHAT_ROOM_ID = 10L;
    private static final Long JOIN_REQUEST_ID = 20L;
    private static final Long OWNER_USER_ID = 1L;
    private static final Long APPLICANT_USER_ID = 2L;
    private static final Long KICKED_USER_ID = 3L;

    // 참여 신청 생성 이벤트 — 방장에게 CHAT_JOIN_REQUEST 알림 저장 검증
    @Test
    void handleJoinRequestCreated_notifiesOwner() {
        handler.handleJoinRequestCreated(
                new JoinRequestCreatedEvent(CHAT_ROOM_ID, JOIN_REQUEST_ID, OWNER_USER_ID));

        verify(notificationService).createNotification(
                eq(OWNER_USER_ID),
                eq(NotificationType.CHAT_JOIN_REQUEST),
                eq("참여 신청 도착"),
                eq("채팅방 참여 신청이 도착했어요."),
                eq(NotificationReferenceType.JOIN_REQUEST),
                eq(JOIN_REQUEST_ID));
    }

    // 참여 신청 수락 이벤트 — 신청자에게 CHAT_JOIN_APPROVED 알림 저장 검증
    @Test
    void handleJoinRequestApproved_notifiesApplicant() {
        handler.handleJoinRequestApproved(
                new JoinRequestApprovedEvent(CHAT_ROOM_ID, JOIN_REQUEST_ID, APPLICANT_USER_ID));

        verify(notificationService).createNotification(
                eq(APPLICANT_USER_ID),
                eq(NotificationType.CHAT_JOIN_APPROVED),
                eq("참여 신청 승인"),
                eq("참여 신청이 승인됐어요."),
                eq(NotificationReferenceType.JOIN_REQUEST),
                eq(JOIN_REQUEST_ID));
    }

    // 참여 신청 거절 이벤트 — 신청자에게 CHAT_JOIN_REJECTED 알림 저장 검증
    @Test
    void handleJoinRequestRejected_notifiesApplicant() {
        handler.handleJoinRequestRejected(
                new JoinRequestRejectedEvent(CHAT_ROOM_ID, JOIN_REQUEST_ID, APPLICANT_USER_ID));

        verify(notificationService).createNotification(
                eq(APPLICANT_USER_ID),
                eq(NotificationType.CHAT_JOIN_REJECTED),
                eq("참여 신청 거절"),
                eq("참여 신청이 거절됐어요."),
                eq(NotificationReferenceType.JOIN_REQUEST),
                eq(JOIN_REQUEST_ID));
    }

    // 멤버 강퇴 이벤트 — 강퇴 대상에게 CHAT_MEMBER_KICKED 알림 저장 검증, referenceType=CHAT_ROOM
    @Test
    void handleChatMemberKicked_notifiesKickedUser() {
        handler.handleChatMemberKicked(
                new ChatMemberKickedEvent(CHAT_ROOM_ID, KICKED_USER_ID));

        verify(notificationService).createNotification(
                eq(KICKED_USER_ID),
                eq(NotificationType.CHAT_MEMBER_KICKED),
                eq("채팅방 강퇴"),
                eq("채팅방에서 강퇴됐어요."),
                eq(NotificationReferenceType.CHAT_ROOM),
                eq(CHAT_ROOM_ID));
    }
}
