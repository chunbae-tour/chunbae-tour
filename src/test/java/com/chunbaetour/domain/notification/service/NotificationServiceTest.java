package com.chunbaetour.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import com.chunbaetour.domain.notification.dto.response.NotificationResponse;
import com.chunbaetour.domain.notification.entity.Notification;
import com.chunbaetour.domain.notification.repository.NotificationRepository;
import com.chunbaetour.domain.notification.type.NotificationReferenceType;
import com.chunbaetour.domain.notification.type.NotificationType;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    private static final Long USER_ID = 1L;

    // 알림 목록 첫 페이지 — cursor null, hasNext false
    @Test
    void getNotifications_firstPage_noCursor() {
        Notification n1 = Notification.builder()
                .userId(USER_ID)
                .type(NotificationType.CHAT_JOIN_REQUEST)
                .title("참여 신청 도착")
                .message("채팅방 참여 신청이 도착했어요.")
                .referenceType(NotificationReferenceType.JOIN_REQUEST)
                .referenceId(10L)
                .build();
        given(notificationRepository.findWithCursor(eq(USER_ID), isNull(), any(PageRequest.class)))
                .willReturn(List.of(n1));

        CursorPageResponse<NotificationResponse> result =
                notificationService.getNotifications(USER_ID, null, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    // 알림 목록 hasNext — size+1 반환 시 nextCursor 설정
    @Test
    void getNotifications_hasNext_nextCursorSet() {
        List<Notification> returned = List.of(
                buildNotification(3L),
                buildNotification(2L),
                buildNotification(1L)  // size+1 번째
        );
        given(notificationRepository.findWithCursor(eq(USER_ID), isNull(), any(PageRequest.class)))
                .willReturn(returned);

        CursorPageResponse<NotificationResponse> result =
                notificationService.getNotifications(USER_ID, null, 2);

        assertThat(result.content()).hasSize(2);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isNotNull();
    }

    // 알림 저장 — 서비스가 파라미터를 올바르게 빌드해 save()에 전달하는지 argThat으로 검증
    @Test
    void createNotification_savesAndReturns() {
        Notification saved = Notification.builder()
                .userId(USER_ID)
                .type(NotificationType.CHAT_JOIN_REQUEST)
                .title("참여 신청 도착")
                .message("채팅방 참여 신청이 도착했어요.")
                .referenceType(NotificationReferenceType.JOIN_REQUEST)
                .referenceId(10L)
                .build();
        given(notificationRepository.save(any(Notification.class))).willReturn(saved);

        Notification result = notificationService.createNotification(
                USER_ID,
                NotificationType.CHAT_JOIN_REQUEST,
                "참여 신청 도착",
                "채팅방 참여 신청이 도착했어요.",
                NotificationReferenceType.JOIN_REQUEST,
                10L);

        verify(notificationRepository).save(argThat(n ->
                n.getUserId().equals(USER_ID)
                && n.getType() == NotificationType.CHAT_JOIN_REQUEST
                && n.getTitle().equals("참여 신청 도착")
                && n.getMessage().equals("채팅방 참여 신청이 도착했어요.")
                && n.getReferenceType() == NotificationReferenceType.JOIN_REQUEST
                && n.getReferenceId().equals(10L)));

        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(result.getType()).isEqualTo(NotificationType.CHAT_JOIN_REQUEST);
        assertThat(result.getReferenceType()).isEqualTo(NotificationReferenceType.JOIN_REQUEST);
        assertThat(result.getReferenceId()).isEqualTo(10L);
    }

    // cursor 전달 시 decodeSafe → cursorId가 repository에 전달되는지 검증 (2페이지 요청)
    @Test
    void getNotifications_withValidCursor_passesDecodedIdToRepo() {
        String cursor = CursorUtils.encode(50L);
        given(notificationRepository.findWithCursor(eq(USER_ID), eq(50L), any(PageRequest.class)))
                .willReturn(List.of());

        notificationService.getNotifications(USER_ID, cursor, 20);

        verify(notificationRepository).findWithCursor(eq(USER_ID), eq(50L), any(PageRequest.class));
    }

    // 잘못된 cursor 전달 시 BusinessException 발생 — decodeSafe가 INVALID_CURSOR 예외 변환
    @Test
    void getNotifications_withInvalidCursor_throwsBusinessException() {
        assertThatThrownBy(() -> notificationService.getNotifications(USER_ID, "not-valid-cursor!!", 20))
                .isInstanceOf(BusinessException.class);
    }

    private Notification buildNotification(Long id) {
        Notification n = Notification.builder()
                .userId(USER_ID)
                .type(NotificationType.CHAT_JOIN_APPROVED)
                .title("참여 신청 승인")
                .message("참여 신청이 승인됐어요.")
                .referenceType(NotificationReferenceType.JOIN_REQUEST)
                .referenceId(10L)
                .build();
        try {
            Field idField = Notification.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(n, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return n;
    }
}
