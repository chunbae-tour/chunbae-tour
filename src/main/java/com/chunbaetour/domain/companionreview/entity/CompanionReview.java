package com.chunbaetour.domain.companionreview.entity;

import com.chunbaetour.domain.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "companion_reviews",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_companion_review",
                columnNames = {"reviewer_id", "target_user_id", "chat_room_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompanionReview extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 리뷰 작성자 — FK to users
    @Column(name = "reviewer_id", nullable = false)
    private Long reviewerId;

    // 리뷰 대상자 — FK to users
    @Column(name = "target_user_id", nullable = false)
    private Long targetUserId;

    // 동행 채팅방 — FK to chat_rooms
    @Column(name = "chat_room_id", nullable = false)
    private Long chatRoomId;

    // 동행 점수 1~5
    @Column(nullable = false)
    private int score;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Builder
    private CompanionReview(Long reviewerId, Long targetUserId, Long chatRoomId, int score, String content) {
        if (reviewerId == null) throw new IllegalArgumentException("reviewerId is required");
        if (targetUserId == null) throw new IllegalArgumentException("targetUserId is required");
        if (chatRoomId == null) throw new IllegalArgumentException("chatRoomId is required");
        if (score < 1 || score > 5) throw new IllegalArgumentException("score must be between 1 and 5");
        this.reviewerId = reviewerId;
        this.targetUserId = targetUserId;
        this.chatRoomId = chatRoomId;
        this.score = score;
        this.content = content;
    }
}
