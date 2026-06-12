package com.chunbaetour.domain.notification.event;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpSubscription;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationEventHandlerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private NotificationRedisPubSubService notificationRedisPubSubService;

    @Mock
    private SimpUserRegistry simpUserRegistry;

    @InjectMocks
    private NotificationEventHandler handler;

    private static final Long CHAT_ROOM_ID = 10L;
    private static final Long JOIN_REQUEST_ID = 20L;
    private static final Long OWNER_USER_ID = 1L;
    private static final Long APPLICANT_USER_ID = 2L;
    private static final Long KICKED_USER_ID = 3L;
    private static final Long SUPPORT_ROOM_ID = 50L;
    private static final Long ROOM_OWNER_USER_ID = 4L;
    private static final Long SENDER_USER_ID = 5L;
    private static final Long RECIPIENT_USER_ID_1 = 6L;
    private static final Long RECIPIENT_USER_ID_2 = 7L;

    // 참여 신청 생성 이벤트 — 방장에게 CHAT_JOIN_REQUEST 알림 저장 + Redis Push 검증, referenceType=CHAT_ROOM (신청 관리 화면 이동용)
    @Test
    void handleJoinRequestCreated_notifiesOwner_andPushes() {
        Notification notification = buildNotification(100L, OWNER_USER_ID, NotificationType.CHAT_JOIN_REQUEST, NotificationReferenceType.CHAT_ROOM, CHAT_ROOM_ID);
        given(notificationService.createNotification(
                eq(OWNER_USER_ID),
                eq(NotificationType.CHAT_JOIN_REQUEST),
                eq("참여 신청 도착"),
                eq("채팅방 참여 신청이 도착했어요."),
                eq(NotificationReferenceType.CHAT_ROOM),
                eq(CHAT_ROOM_ID))).willReturn(notification);

        handler.handleJoinRequestCreated(
                new JoinRequestCreatedEvent(CHAT_ROOM_ID, JOIN_REQUEST_ID, OWNER_USER_ID));

        verify(notificationService).createNotification(
                eq(OWNER_USER_ID),
                eq(NotificationType.CHAT_JOIN_REQUEST),
                eq("참여 신청 도착"),
                eq("채팅방 참여 신청이 도착했어요."),
                eq(NotificationReferenceType.CHAT_ROOM),
                eq(CHAT_ROOM_ID));
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

    // 메시지 전송 이벤트 — 수신 대상 모두 미연결(오프라인) → 각각 CHAT_MESSAGE 알림 저장 + Redis Push 검증
    @Test
    void handleChatMessageSent_notifiesEachRecipient_andPushes() {
        given(simpUserRegistry.getUser(any())).willReturn(null);
        Notification notification1 = buildNotification(110L, RECIPIENT_USER_ID_1, NotificationType.CHAT_MESSAGE, NotificationReferenceType.CHAT_ROOM, CHAT_ROOM_ID);
        Notification notification2 = buildNotification(111L, RECIPIENT_USER_ID_2, NotificationType.CHAT_MESSAGE, NotificationReferenceType.CHAT_ROOM, CHAT_ROOM_ID);
        given(notificationService.createNotification(
                eq(RECIPIENT_USER_ID_1), eq(NotificationType.CHAT_MESSAGE), eq("새 채팅 메시지"),
                eq("채팅방에 새 메시지가 도착했어요."), eq(NotificationReferenceType.CHAT_ROOM), eq(CHAT_ROOM_ID)))
                .willReturn(notification1);
        given(notificationService.createNotification(
                eq(RECIPIENT_USER_ID_2), eq(NotificationType.CHAT_MESSAGE), eq("새 채팅 메시지"),
                eq("채팅방에 새 메시지가 도착했어요."), eq(NotificationReferenceType.CHAT_ROOM), eq(CHAT_ROOM_ID)))
                .willReturn(notification2);

        handler.handleChatMessageSent(new ChatMessageSentEvent(
                CHAT_ROOM_ID, SENDER_USER_ID, List.of(RECIPIENT_USER_ID_1, RECIPIENT_USER_ID_2)));

        verify(notificationRedisPubSubService).publish(eq(RECIPIENT_USER_ID_1), any(NotificationResponse.class));
        verify(notificationRedisPubSubService).publish(eq(RECIPIENT_USER_ID_2), any(NotificationResponse.class));
    }

    // 채팅방 화면 보는 중(해당 방 토픽 구독) 수신자 — 알림 생성/Push 모두 skip (정책 안B 확장판)
    @Test
    void handleChatMessageSent_recipientViewingRoom_isSkipped() {
        SimpUser viewingUser = mockSubscribedUser("/sub/chat/rooms/" + CHAT_ROOM_ID);
        given(simpUserRegistry.getUser(String.valueOf(RECIPIENT_USER_ID_1))).willReturn(viewingUser);
        given(simpUserRegistry.getUser(String.valueOf(RECIPIENT_USER_ID_2))).willReturn(null);
        Notification notification2 = buildNotification(111L, RECIPIENT_USER_ID_2, NotificationType.CHAT_MESSAGE, NotificationReferenceType.CHAT_ROOM, CHAT_ROOM_ID);
        given(notificationService.createNotification(
                eq(RECIPIENT_USER_ID_2), eq(NotificationType.CHAT_MESSAGE), eq("새 채팅 메시지"),
                eq("채팅방에 새 메시지가 도착했어요."), eq(NotificationReferenceType.CHAT_ROOM), eq(CHAT_ROOM_ID)))
                .willReturn(notification2);

        handler.handleChatMessageSent(new ChatMessageSentEvent(
                CHAT_ROOM_ID, SENDER_USER_ID, List.of(RECIPIENT_USER_ID_1, RECIPIENT_USER_ID_2)));

        verify(notificationService, never()).createNotification(eq(RECIPIENT_USER_ID_1), any(), any(), any(), any(), any());
        verify(notificationRedisPubSubService, never()).publish(eq(RECIPIENT_USER_ID_1), any());
        verify(notificationRedisPubSubService).publish(eq(RECIPIENT_USER_ID_2), any(NotificationResponse.class));
    }

    // 온라인이지만 해당 채팅방 미구독(다른 화면) 수신자 — CHAT_MESSAGE 알림 발송 (안B 확장판 핵심 변경)
    @Test
    void handleChatMessageSent_recipientOnlineButNotViewingRoom_isNotified() {
        SimpUser onlineOtherPageUser = mockSubscribedUser("/user/queue/notifications");
        given(simpUserRegistry.getUser(String.valueOf(RECIPIENT_USER_ID_1))).willReturn(onlineOtherPageUser);
        Notification notification1 = buildNotification(110L, RECIPIENT_USER_ID_1, NotificationType.CHAT_MESSAGE, NotificationReferenceType.CHAT_ROOM, CHAT_ROOM_ID);
        given(notificationService.createNotification(
                eq(RECIPIENT_USER_ID_1), eq(NotificationType.CHAT_MESSAGE), eq("새 채팅 메시지"),
                eq("채팅방에 새 메시지가 도착했어요."), eq(NotificationReferenceType.CHAT_ROOM), eq(CHAT_ROOM_ID)))
                .willReturn(notification1);

        handler.handleChatMessageSent(new ChatMessageSentEvent(
                CHAT_ROOM_ID, SENDER_USER_ID, List.of(RECIPIENT_USER_ID_1)));

        verify(notificationRedisPubSubService).publish(eq(RECIPIENT_USER_ID_1), any(NotificationResponse.class));
    }

    // 다른 채팅방 토픽 구독 중인 수신자 — 해당 방(CHAT_ROOM_ID) 미구독이므로 CHAT_MESSAGE 알림 발송 (prefix 매칭 회귀 가드)
    @Test
    void handleChatMessageSent_recipientViewingOtherRoom_isNotified() {
        Long otherChatRoomId = CHAT_ROOM_ID + 1;
        SimpUser viewingOtherRoomUser = mockSubscribedUser("/sub/chat/rooms/" + otherChatRoomId);
        given(simpUserRegistry.getUser(String.valueOf(RECIPIENT_USER_ID_1))).willReturn(viewingOtherRoomUser);
        Notification notification1 = buildNotification(112L, RECIPIENT_USER_ID_1, NotificationType.CHAT_MESSAGE, NotificationReferenceType.CHAT_ROOM, CHAT_ROOM_ID);
        given(notificationService.createNotification(
                eq(RECIPIENT_USER_ID_1), eq(NotificationType.CHAT_MESSAGE), eq("새 채팅 메시지"),
                eq("채팅방에 새 메시지가 도착했어요."), eq(NotificationReferenceType.CHAT_ROOM), eq(CHAT_ROOM_ID)))
                .willReturn(notification1);

        handler.handleChatMessageSent(new ChatMessageSentEvent(
                CHAT_ROOM_ID, SENDER_USER_ID, List.of(RECIPIENT_USER_ID_1)));

        verify(notificationRedisPubSubService).publish(eq(RECIPIENT_USER_ID_1), any(NotificationResponse.class));
    }

    // 일부 수신자 알림 저장 실패 — 해당 수신자만 건너뛰고 나머지는 정상 처리 (둘 다 오프라인 가정)
    @Test
    void handleChatMessageSent_oneRecipientFails_othersStillNotified() {
        given(simpUserRegistry.getUser(any())).willReturn(null);
        Notification notification2 = buildNotification(111L, RECIPIENT_USER_ID_2, NotificationType.CHAT_MESSAGE, NotificationReferenceType.CHAT_ROOM, CHAT_ROOM_ID);
        given(notificationService.createNotification(
                eq(RECIPIENT_USER_ID_1), any(), any(), any(), any(), any()))
                .willThrow(new RuntimeException("DB error"));
        given(notificationService.createNotification(
                eq(RECIPIENT_USER_ID_2), eq(NotificationType.CHAT_MESSAGE), eq("새 채팅 메시지"),
                eq("채팅방에 새 메시지가 도착했어요."), eq(NotificationReferenceType.CHAT_ROOM), eq(CHAT_ROOM_ID)))
                .willReturn(notification2);

        handler.handleChatMessageSent(new ChatMessageSentEvent(
                CHAT_ROOM_ID, SENDER_USER_ID, List.of(RECIPIENT_USER_ID_1, RECIPIENT_USER_ID_2)));

        verify(notificationRedisPubSubService, never()).publish(eq(RECIPIENT_USER_ID_1), any());
        verify(notificationRedisPubSubService).publish(eq(RECIPIENT_USER_ID_2), any(NotificationResponse.class));
    }

    // Redis Push 실패 시 예외 미전파 — 알림 저장 롤백 없음 보장
    @Test
    void handleJoinRequestCreated_pushFailure_doesNotPropagateException() {
        Notification notification = buildNotification(100L, OWNER_USER_ID, NotificationType.CHAT_JOIN_REQUEST, NotificationReferenceType.CHAT_ROOM, CHAT_ROOM_ID);
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

    // 상담 메시지 전송 이벤트 — 방 소유자에게 SUPPORT_MESSAGE 알림 저장 + Redis Push 검증
    @Test
    void handleSupportMessageSent_notifiesRoomOwner_andPushes() {
        Notification notification = buildNotification(200L, ROOM_OWNER_USER_ID, NotificationType.SUPPORT_MESSAGE, NotificationReferenceType.SUPPORT_ROOM, SUPPORT_ROOM_ID);
        given(notificationService.createNotification(
                eq(ROOM_OWNER_USER_ID),
                eq(NotificationType.SUPPORT_MESSAGE),
                eq("고객센터 메시지 도착"),
                eq("고객센터 상담 메시지가 도착했어요."),
                eq(NotificationReferenceType.SUPPORT_ROOM),
                eq(SUPPORT_ROOM_ID))).willReturn(notification);

        handler.handleSupportMessageSent(new SupportMessageSentEvent(SUPPORT_ROOM_ID, ROOM_OWNER_USER_ID));

        verify(notificationService).createNotification(
                eq(ROOM_OWNER_USER_ID),
                eq(NotificationType.SUPPORT_MESSAGE),
                eq("고객센터 메시지 도착"),
                eq("고객센터 상담 메시지가 도착했어요."),
                eq(NotificationReferenceType.SUPPORT_ROOM),
                eq(SUPPORT_ROOM_ID));
        verify(notificationRedisPubSubService).publish(eq(ROOM_OWNER_USER_ID), any(NotificationResponse.class));
    }

    // createNotification 실패 시 push 미호출 — 저장 실패가 Push로 전파되지 않음
    @Test
    void handleSupportMessageSent_createFails_noPush() {
        given(notificationService.createNotification(any(), any(), any(), any(), any(), any()))
                .willThrow(new RuntimeException("DB error"));

        handler.handleSupportMessageSent(new SupportMessageSentEvent(SUPPORT_ROOM_ID, ROOM_OWNER_USER_ID));

        verify(notificationRedisPubSubService, never()).publish(any(), any());
    }

    // 상담 종료 이벤트 — 방 소유자에게 SUPPORT_ROOM_CLOSED 알림 저장 + Redis Push 검증
    @Test
    void handleSupportRoomClosed_notifiesRoomOwner_andPushes() {
        Notification notification = buildNotification(201L, ROOM_OWNER_USER_ID, NotificationType.SUPPORT_ROOM_CLOSED, NotificationReferenceType.SUPPORT_ROOM, SUPPORT_ROOM_ID);
        given(notificationService.createNotification(
                eq(ROOM_OWNER_USER_ID),
                eq(NotificationType.SUPPORT_ROOM_CLOSED),
                eq("상담 종료"),
                eq("고객센터 상담이 종료됐어요."),
                eq(NotificationReferenceType.SUPPORT_ROOM),
                eq(SUPPORT_ROOM_ID))).willReturn(notification);

        handler.handleSupportRoomClosed(new SupportRoomClosedEvent(SUPPORT_ROOM_ID, ROOM_OWNER_USER_ID));

        verify(notificationService).createNotification(
                eq(ROOM_OWNER_USER_ID),
                eq(NotificationType.SUPPORT_ROOM_CLOSED),
                eq("상담 종료"),
                eq("고객센터 상담이 종료됐어요."),
                eq(NotificationReferenceType.SUPPORT_ROOM),
                eq(SUPPORT_ROOM_ID));
        verify(notificationRedisPubSubService).publish(eq(ROOM_OWNER_USER_ID), any(NotificationResponse.class));
    }

    // 상담 종료 createNotification 실패 시 push 미호출
    @Test
    void handleSupportRoomClosed_createFails_noPush() {
        given(notificationService.createNotification(any(), any(), any(), any(), any(), any()))
                .willThrow(new RuntimeException("DB error"));

        handler.handleSupportRoomClosed(new SupportRoomClosedEvent(SUPPORT_ROOM_ID, ROOM_OWNER_USER_ID));

        verify(notificationRedisPubSubService, never()).publish(any(), any());
    }

    // Support 메시지 핸들러 — Push 실패 시 예외 미전파 (저장은 완료됨)
    @Test
    void handleSupportMessageSent_pushFailure_doesNotPropagateException() {
        Notification notification = buildNotification(200L, ROOM_OWNER_USER_ID, NotificationType.SUPPORT_MESSAGE, NotificationReferenceType.SUPPORT_ROOM, SUPPORT_ROOM_ID);
        given(notificationService.createNotification(any(), any(), any(), any(), any(), any()))
                .willReturn(notification);
        org.mockito.BDDMockito.willThrow(new RuntimeException("Redis unavailable"))
                .given(notificationRedisPubSubService).publish(any(), any());

        assertThatNoException().isThrownBy(() ->
                handler.handleSupportMessageSent(new SupportMessageSentEvent(SUPPORT_ROOM_ID, ROOM_OWNER_USER_ID)));
    }

    // Support 종료 핸들러 — Push 실패 시 예외 미전파 (저장은 완료됨)
    @Test
    void handleSupportRoomClosed_pushFailure_doesNotPropagateException() {
        Notification notification = buildNotification(201L, ROOM_OWNER_USER_ID, NotificationType.SUPPORT_ROOM_CLOSED, NotificationReferenceType.SUPPORT_ROOM, SUPPORT_ROOM_ID);
        given(notificationService.createNotification(any(), any(), any(), any(), any(), any()))
                .willReturn(notification);
        org.mockito.BDDMockito.willThrow(new RuntimeException("Redis unavailable"))
                .given(notificationRedisPubSubService).publish(any(), any());

        assertThatNoException().isThrownBy(() ->
                handler.handleSupportRoomClosed(new SupportRoomClosedEvent(SUPPORT_ROOM_ID, ROOM_OWNER_USER_ID)));
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

    // 단일 세션이 destination 하나를 구독 중인 SimpUser mock 생성
    private SimpUser mockSubscribedUser(String destination) {
        SimpSubscription subscription = mock(SimpSubscription.class);
        given(subscription.getDestination()).willReturn(destination);
        SimpSession session = mock(SimpSession.class);
        given(session.getSubscriptions()).willReturn(Set.of(subscription));
        SimpUser user = mock(SimpUser.class);
        given(user.getSessions()).willReturn(Set.of(session));
        return user;
    }
}
