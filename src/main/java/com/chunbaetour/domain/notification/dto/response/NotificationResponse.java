package com.chunbaetour.domain.notification.dto.response;

import com.chunbaetour.domain.notification.entity.Notification;
import com.chunbaetour.domain.notification.type.NotificationReferenceType;
import com.chunbaetour.domain.notification.type.NotificationType;
import java.time.LocalDateTime;

public record NotificationResponse(
        Long notificationId,
        NotificationType type,
        String title,
        String message,
        Long referenceId,
        NotificationReferenceType referenceType,
        boolean isRead,
        LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getReferenceId(),
                notification.getReferenceType(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}
