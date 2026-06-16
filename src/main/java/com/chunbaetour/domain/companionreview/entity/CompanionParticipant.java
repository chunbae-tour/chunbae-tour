package com.chunbaetour.domain.companionreview.entity;

import com.chunbaetour.domain.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "companion_participants",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_companion_participant",
                columnNames = {"companion_id", "user_id"}
        ),
        indexes = @Index(name = "idx_companion_participants_user_id", columnList = "user_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompanionParticipant extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 소속 동행 — FK to companions
    @Column(name = "companion_id", nullable = false)
    private Long companionId;

    // 참여자 — FK to users
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "added_at", nullable = false)
    private LocalDateTime addedAt;

    // 참여자 본인의 동행 종료 시각 — null이면 아직 종료 안 함 (고도화 #2)
    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Builder
    private CompanionParticipant(Long companionId, Long userId) {
        if (companionId == null) throw new IllegalArgumentException("companionId is required");
        if (userId == null) throw new IllegalArgumentException("userId is required");
        this.companionId = companionId;
        this.userId = userId;
        this.addedAt = LocalDateTime.now();
    }
}
