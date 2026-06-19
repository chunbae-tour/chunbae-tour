package com.chunbaetour.domain.notification.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.common.util.CursorUtils;
import com.chunbaetour.domain.notification.dto.response.NotificationResponse;
import com.chunbaetour.domain.notification.entity.Notification;
import com.chunbaetour.domain.notification.repository.NotificationRepository;
import com.chunbaetour.domain.notification.type.NotificationReferenceType;
import com.chunbaetour.domain.notification.type.NotificationType;
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

        // 슬라이스·매핑·nextCursor·size echo 공통 진입점 단일화 (KAN-325)
        return CursorPageResponse.of(notifications, size, NotificationResponse::from, Notification::getId);
    }

    // 단건 읽음 처리 — 본인 알림 아닌 경우 NOTIFICATION_NOT_FOUND (정보 비노출)
    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
        notification.markAsRead();
    }

    // 전체 읽음 처리 — userId 기준 isRead=false 일괄 업데이트
    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsRead(userId);
    }

    // 알림 삭제 — soft delete, 이미 삭제된 경우 204 멱등, 본인 알림 아닌 경우 NOTIFICATION_NOT_FOUND (정보 비노출)
    @Transactional
    public void deleteNotification(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndUserIdIncludingDeleted(notificationId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
        notification.delete(); // idempotent — deletedAt null 일 때만 설정
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
