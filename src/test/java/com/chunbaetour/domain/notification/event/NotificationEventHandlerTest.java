package com.chunbaetour.domain.notification.event;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.chat.event.ChatMemberKickedEvent;
import com.chunbaetour.domain.chat.event.JoinRequestApprovedEvent;
import com.chunbaetour.domain.chat.event.JoinRequestCreatedEvent;
import com.chunbaetour.domain.chat.event.JoinRequestRejectedEvent;
import com.chunbaetour.domain.notification.dto.response.NotificationResponse;
import com.chunbaetour.domain.notification.entity.Notification;
import com.chunbaetour.domain.notification.service.NotificationRedisPubSubService;
import com.chunbaetour.domain.notification.service.NotificationService;
import com.chunbaetour.domain.notification.type.NotificationReferenceType;
import com.chunbaetour.domain.notification.type.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationEventHandlerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private NotificationRedisPubSubService notificationRedisPubSubService;

    @InjectMocks
    private NotificationEventHandler handler;

    private static final Long CHAT_ROOM_ID = 10L;
    private static final Long JOIN_REQUEST_ID = 20L;
    private static final Long OWNER_USER_ID = 1L;
    private static final Long APPLICANT_USER_ID = 2L;
    private static final Long KICKED_USER_ID = 3L;

    // 참여 신청 생성 이벤트 — 방장에게 CHAT_JOIN_REQUEST 알림 저장 + Redis Push 검증
    @Test
    void handleJoinRequestCreated_notifiesOwner_andPushes() {
        Notification notification = buildNotification(100L, OWNER_USER_ID, NotificationType.CHAT_JOIN_REQUEST, NotificationReferenceType.JOIN_REQUEST, JOIN_REQUEST_ID);
        given(notificationService.createNotification(
                eq(OWNER_USER_ID),
                eq(NotificationType.CHAT_JOIN_REQUEST),
                eq("참여 신청 도착"),
                eq("채팅방 참여 신청이 도착했어요."),
                eq(NotificationReferenceType.JOIN_REQUEST),
                eq(JOIN_REQUEST_ID))).willReturn(notification);

        handler.handleJoinRequestCreated(
                new JoinRequestCreatedEvent(CHAT_ROOM_ID, JOIN_REQUEST_ID, OWNER_USER_ID));

        verify(notificationService).createNotification(
                eq(OWNER_USER_ID),
                eq(NotificationType.CHAT_JOIN_REQUEST),
                eq("참여 신청 도착"),
                eq("채팅방 참여 신청이 도착했어요."),
                eq(NotificationReferenceType.JOIN_REQUEST),
                eq(JOIN_REQUEST_ID));
        verify(notificationRedisPubSubService).publish(
                eq(OWNER_USER_ID),
                any(NotificationResponse.class));
    }

    // 참여 신청 수락 이벤트 — 신청자에게 CHAT_JOIN_APPROVED 알림 저장 + Redis Push 검증 (referenceType=CHAT_ROOM)
    @Test
    void handleJoinRequestApproved_notifiesApplicant_andPushes() {
        Notification notification = buildNotification(101L, APPLICANT_USER_ID, NotificationType.CHAT_JOIN_APPROVED, NotificationReferenceType.CHAT_ROOM, CHAT_ROOM_ID);
        given(notificationService.createNotification(
                eq(APPLICANT_USER_ID),
                eq(NotificationType.CHAT_JOIN_APPROVED),
                eq("참여 신청 승인"),
                eq("참여 신청이 승인됐어요."),
                eq(NotificationReferenceType.CHAT_ROOM),
                eq(CHAT_ROOM_ID))).willReturn(notification);

        handler.handleJoinRequestApproved(
                new JoinRequestApprovedEvent(CHAT_ROOM_ID, JOIN_REQUEST_ID, APPLICANT_USER_ID));

        verify(notificationService).createNotification(
                eq(APPLICANT_USER_ID),
                eq(NotificationType.CHAT_JOIN_APPROVED),
                eq("참여 신청 승인"),
                eq("참여 신청이 승인됐어요."),
                eq(NotificationReferenceType.CHAT_ROOM),
                eq(CHAT_ROOM_ID));
        verify(notificationRedisPubSubService).publish(
                eq(APPLICANT_USER_ID),
                any(NotificationResponse.class));
    }

    // 참여 신청 거절 이벤트 — 신청자에게 CHAT_JOIN_REJECTED 알림 저장 + Redis Push 검증 (referenceType=CHAT_ROOM)
    @Test
    void handleJoinRequestRejected_notifiesApplicant_andPushes() {
        Notification notification = buildNotification(102L, APPLICANT_USER_ID, NotificationType.CHAT_JOIN_REJECTED, NotificationReferenceType.CHAT_ROOM, CHAT_ROOM_ID);
        given(notificationService.createNotification(
                eq(APPLICANT_USER_ID),
                eq(NotificationType.CHAT_JOIN_REJECTED),
                eq("참여 신청 거절"),
                eq("참여 신청이 거절됐어요."),
                eq(NotificationReferenceType.CHAT_ROOM),
                eq(CHAT_ROOM_ID))).willReturn(notification);

        handler.handleJoinRequestRejected(
                new JoinRequestRejectedEvent(CHAT_ROOM_ID, JOIN_REQUEST_ID, APPLICANT_USER_ID));

        verify(notificationService).createNotification(
                eq(APPLICANT_USER_ID),
                eq(NotificationType.CHAT_JOIN_REJECTED),
                eq("참여 신청 거절"),
                eq("참여 신청이 거절됐어요."),
                eq(NotificationReferenceType.CHAT_ROOM),
                eq(CHAT_ROOM_ID));
        verify(notificationRedisPubSubService).publish(
                eq(APPLICANT_USER_ID),
                any(NotificationResponse.class));
    }

    // 멤버 강퇴 이벤트 — 강퇴 대상에게 CHAT_MEMBER_KICKED 알림 저장 + Redis Push 검증
    @Test
    void handleChatMemberKicked_notifiesKickedUser_andPushes() {
        Notification notification = buildNotification(103L, KICKED_USER_ID, NotificationType.CHAT_MEMBER_KICKED, NotificationReferenceType.CHAT_ROOM, CHAT_ROOM_ID);
        given(notificationService.createNotification(
                eq(KICKED_USER_ID),
                eq(NotificationType.CHAT_MEMBER_KICKED),
                eq("채팅방 강퇴"),
                eq("채팅방에서 강퇴됐어요."),
                eq(NotificationReferenceType.CHAT_ROOM),
                eq(CHAT_ROOM_ID))).willReturn(notification);

        handler.handleChatMemberKicked(
                new ChatMemberKickedEvent(CHAT_ROOM_ID, KICKED_USER_ID));

        verify(notificationService).createNotification(
                eq(KICKED_USER_ID),
                eq(NotificationType.CHAT_MEMBER_KICKED),
                eq("채팅방 강퇴"),
                eq("채팅방에서 강퇴됐어요."),
                eq(NotificationReferenceType.CHAT_ROOM),
                eq(CHAT_ROOM_ID));
        verify(notificationRedisPubSubService).publish(
                eq(KICKED_USER_ID),
                any(NotificationResponse.class));
    }

    // Redis Push 실패 시 예외 미전파 — 알림 저장 롤백 없음 보장
    @Test
    void handleJoinRequestCreated_pushFailure_doesNotPropagateException() {
        Notification notification = buildNotification(100L, OWNER_USER_ID, NotificationType.CHAT_JOIN_REQUEST, NotificationReferenceType.JOIN_REQUEST, JOIN_REQUEST_ID);
        given(notificationService.createNotification(any(), any(), any(), any(), any(), any()))
                .willReturn(notification);
        willThrow(new RuntimeException("Redis unavailable"))
                .given(notificationRedisPubSubService).publish(any(), any());

        assertThatNoException().isThrownBy(() ->
                handler.handleJoinRequestCreated(
                        new JoinRequestCreatedEvent(CHAT_ROOM_ID, JOIN_REQUEST_ID, OWNER_USER_ID)));
    }

    // createNotification 예외 시 push 미호출 — 저장 실패 경로에서 Redis 전송 차단 검증
    @Test
    void handleJoinRequestCreated_createNotificationFails_noPush() {
        given(notificationService.createNotification(any(), any(), any(), any(), any(), any()))
                .willThrow(new RuntimeException("DB error"));

        handler.handleJoinRequestCreated(
                new JoinRequestCreatedEvent(CHAT_ROOM_ID, JOIN_REQUEST_ID, OWNER_USER_ID));

        verify(notificationRedisPubSubService, never()).publish(any(), any());
    }

    private Notification buildNotification(Long id, Long userId, NotificationType type, NotificationReferenceType referenceType, Long referenceId) {
        Notification n = Notification.builder()
                .userId(userId)
                .type(type)
                .title("테스트")
                .message("테스트 메시지")
                .referenceType(referenceType)
                .referenceId(referenceId)
                .build();
        ReflectionTestUtils.setField(n, "id", id);
        return n;
    }
}
