package com.chunbaetour.domain.chat.entity;

import com.chunbaetour.domain.chat.type.JoinRequestStatus;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "join_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class JoinRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_room_id", nullable = false)
    private Long chatRoomId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JoinRequestStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private JoinRequest(Long chatRoomId, Long userId, String message) {
        if (chatRoomId == null || userId == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        this.chatRoomId = chatRoomId;
        this.userId = userId;
        this.message = message;
        this.status = JoinRequestStatus.PENDING;
    }

    // PENDING 상태에서만 전이 허용 — 이미 처리된 신청 재처리 차단 (CHAT_012)
    public void approve() {
        if (!isPending()) {
            throw new BusinessException(ErrorCode.CHAT_APPLICATION_ALREADY_PROCESSED);
        }
        this.status = JoinRequestStatus.APPROVED;
    }

    public void reject() {
        if (!isPending()) {
            throw new BusinessException(ErrorCode.CHAT_APPLICATION_ALREADY_PROCESSED);
        }
        this.status = JoinRequestStatus.REJECTED;
    }

    // 중복 처리 방지용 선행 체크 — approve/reject 호출 전 반드시 검사
    public boolean isPending() {
        return this.status == JoinRequestStatus.PENDING;
    }
}
