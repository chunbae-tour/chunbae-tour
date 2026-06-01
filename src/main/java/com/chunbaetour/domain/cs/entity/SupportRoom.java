package com.chunbaetour.domain.cs.entity;

import com.chunbaetour.domain.common.entity.BaseEntity;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "support_rooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupportRoom extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // USER가 상담을 요청한 사용자 — FK to users
    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 상담을 처리하는 ADMIN — 배정 전 null
    @Column(name = "admin_id")
    private Long adminId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private SupportRoomStatus status;

    // 상담 종료 시각 — WAITING/IN_PROGRESS 상태에서는 null
    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Builder
    private SupportRoom(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        this.userId = userId;
        this.status = SupportRoomStatus.WAITING;
    }

    // ADMIN 배정 — WAITING → IN_PROGRESS 전이, @Transactional 경계 안 영속 상태에서 호출
    public void assignAdmin(Long adminId) {
        if (adminId == null) {
            throw new IllegalArgumentException("adminId must not be null");
        }
        this.adminId = adminId;
        this.status = SupportRoomStatus.IN_PROGRESS;
    }

    // 상담 종료 — closedAt은 호출자가 Clock으로 주입 (테스트 결정성 + createdAt/updatedAt 기준 통일)
    public void close(Clock clock) {
        if (this.status == SupportRoomStatus.CLOSED) {
            throw new BusinessException(ErrorCode.SUPPORT_ROOM_ALREADY_CLOSED);
        }
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        this.status = SupportRoomStatus.CLOSED;
        this.closedAt = LocalDateTime.now(clock);
    }
}
