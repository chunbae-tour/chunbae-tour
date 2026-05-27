package com.chunbaetour.domain.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.common.response.ApiResponse;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.notification.dto.response.NotificationResponse;
import com.chunbaetour.domain.notification.service.NotificationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationController notificationController;

    private static final Long USER_ID = 1L;

    // 알림 목록 조회 — service 위임 및 ApiResponse 래핑 검증
    @Test
    void getNotifications_delegatesToService_andWrapsResponse() {
        CursorPageResponse<NotificationResponse> page =
                new CursorPageResponse<>(List.of(), null, false, 20);
        given(notificationService.getNotifications(USER_ID, null, 20)).willReturn(page);

        ApiResponse<CursorPageResponse<NotificationResponse>> response =
                notificationController.getNotifications(USER_ID, null, 20);

        verify(notificationService).getNotifications(USER_ID, null, 20);
        assertThat(response.data()).isEqualTo(page);
    }

    // cursor 전달 시 service에 그대로 전달되는지 검증
    @Test
    void getNotifications_passesCursorToService() {
        String cursor = "eyJpZCI6OTh9";
        CursorPageResponse<NotificationResponse> page =
                new CursorPageResponse<>(List.of(), null, false, 10);
        given(notificationService.getNotifications(USER_ID, cursor, 10)).willReturn(page);

        notificationController.getNotifications(USER_ID, cursor, 10);

        verify(notificationService).getNotifications(USER_ID, cursor, 10);
    }
}
