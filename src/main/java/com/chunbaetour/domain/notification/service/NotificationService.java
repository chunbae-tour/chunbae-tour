package com.chunbaetour.domain.notification.service;

import com.chunbaetour.domain.notification.dto.response.NotificationResponse;
import com.chunbaetour.domain.notification.entity.Notification;
import com.chunbaetour.domain.notification.repository.NotificationRepository;
import com.chunbaetour.domain.notification.type.NotificationReferenceType;
import com.chunbaetour.domain.notification.type.NotificationType;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;

    // 알림 목록 조회 — userId 기준 id DESC 커서 페이징, soft delete 제외(@SQLRestriction)
    public CursorPageResponse<NotificationResponse> getNotifications(Long userId, String cursor, int size) {
        Long cursorId = CursorUtils.decodeSafe(cursor);
        List<Notification> notifications = notificationRepository.findWithCursor(
                userId, cursorId, PageRequest.of(0, size + 1));

        boolean hasNext = notifications.size() > size;
        List<Notification> page = hasNext ? notifications.subList(0, size) : notifications;

        String nextCursor = hasNext ? CursorUtils.encode(page.get(page.size() - 1).getId()) : null;

        return new CursorPageResponse<>(
                page.stream().map(NotificationResponse::from).toList(),
                nextCursor,
                hasNext,
                size
        );
    }

    // 알림 저장 — NotificationEventHandler에서 REQUIRES_NEW 트랜잭션으로 호출
    @Transactional
    public Notification createNotification(
            Long userId,
            NotificationType type,
            String title,
            String message,
            NotificationReferenceType referenceType,
            Long referenceId) {
        return notificationRepository.save(
                Notification.builder()
                        .userId(userId)
                        .type(type)
                        .title(title)
                        .message(message)
                        .referenceType(referenceType)
                        .referenceId(referenceId)
                        .build());
    }
}
