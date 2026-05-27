package com.chunbaetour.domain.notification.entity;

import com.chunbaetour.domain.notification.type.NotificationReferenceType;
import com.chunbaetour.domain.notification.type.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

// ERD: notifications — updatedAt 없음, soft delete(deleted_at) 구조
@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_user_id_id", columnList = "user_id, id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@SQLRestriction("deleted_at IS NULL")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private NotificationType type;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false, length = 20)
    private NotificationReferenceType referenceType;

    @Column(name = "reference_id", nullable = false)
    private Long referenceId;

    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    private Notification(Long userId, NotificationType type, String title, String message,
                         NotificationReferenceType referenceType, Long referenceId) {
        if (userId == null) throw new IllegalArgumentException("userId is required");
        if (type == null) throw new IllegalArgumentException("type is required");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title is required");
        if (message == null || message.isBlank()) throw new IllegalArgumentException("message is required");
        if (referenceType == null) throw new IllegalArgumentException("referenceType is required");
        if (referenceId == null) throw new IllegalArgumentException("referenceId is required");
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
    }

    // 읽음 처리
    public void markAsRead() {
        this.isRead = true;
    }

    // soft delete — 최초 1회만 설정, 재호출 시 최초 삭제 시점 보존
    public void delete() {
        if (this.deletedAt == null) {
            this.deletedAt = LocalDateTime.now();
        }
    }
}
