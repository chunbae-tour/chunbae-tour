package com.chunbaetour.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.notification.entity.Notification;
import com.chunbaetour.domain.notification.repository.NotificationRepository;
import com.chunbaetour.domain.notification.type.NotificationReferenceType;
import com.chunbaetour.domain.notification.type.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    // 알림 저장 — repository.save() 호출 및 반환 엔티티 일치 검증
    @Test
    void createNotification_savesAndReturns() {
        Notification saved = Notification.builder()
                .userId(1L)
                .type(NotificationType.CHAT_JOIN_REQUEST)
                .title("참여 신청 도착")
                .message("채팅방 참여 신청이 도착했어요.")
                .referenceType(NotificationReferenceType.JOIN_REQUEST)
                .referenceId(10L)
                .build();
        given(notificationRepository.save(any(Notification.class))).willReturn(saved);

        Notification result = notificationService.createNotification(
                1L,
                NotificationType.CHAT_JOIN_REQUEST,
                "참여 신청 도착",
                "채팅방 참여 신청이 도착했어요.",
                NotificationReferenceType.JOIN_REQUEST,
                10L);

        verify(notificationRepository).save(any(Notification.class));
        assertThat(result.getUserId()).isEqualTo(1L);
        assertThat(result.getType()).isEqualTo(NotificationType.CHAT_JOIN_REQUEST);
        assertThat(result.getReferenceType()).isEqualTo(NotificationReferenceType.JOIN_REQUEST);
        assertThat(result.getReferenceId()).isEqualTo(10L);
    }
}
