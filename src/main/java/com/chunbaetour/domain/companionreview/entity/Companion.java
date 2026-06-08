package com.chunbaetour.domain.companionreview.entity;

import com.chunbaetour.domain.common.entity.BaseEntity;
import com.chunbaetour.domain.companionreview.type.CompanionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "companions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_companions_chat_room_id",
                columnNames = "chat_room_id"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Companion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 동행 채팅방 — FK to chat_rooms, UNIQUE (방당 동행 1번만)
    @Column(name = "chat_room_id", nullable = false)
    private Long chatRoomId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CompanionStatus status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Builder
    private Companion(Long chatRoomId) {
        if (chatRoomId == null) throw new IllegalArgumentException("chatRoomId is required");
        this.chatRoomId = chatRoomId;
        this.status = CompanionStatus.ONGOING;
        this.startedAt = LocalDateTime.now();
    }
}
