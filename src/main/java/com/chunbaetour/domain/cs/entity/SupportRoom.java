package com.chunbaetour.domain.cs.entity;

import com.chunbaetour.domain.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    // 상담 종료 시 ADMIN 메모 — 선택
    @Column(columnDefinition = "TEXT")
    private String summary;

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
}
