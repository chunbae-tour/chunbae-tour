package com.chunbaetour.domain.like.entity;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.like.type.LikeTargetType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(
    name = "user_likes",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_likes_type_target", columnNames = {"user_id", "target_type", "target_id"})
    },
    indexes = {
        @Index(name = "idx_user_likes_user", columnList = "user_id"),
        @Index(name = "idx_user_likes_target", columnList = "target_type, target_id")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class UserLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Account user;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 50)
    private LikeTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private UserLike(Account user, LikeTargetType targetType, Long targetId) {
        if (user == null) {
            throw new IllegalArgumentException("사용자 정보는 필수입니다.");
        }
        if (targetType == null) {
            throw new IllegalArgumentException("찜 대상 타입은 필수입니다.");
        }
        if (targetId == null) {
            throw new IllegalArgumentException("찜 대상 ID는 필수입니다.");
        }
        this.user = user;
        this.targetType = targetType;
        this.targetId = targetId;
    }

    public static UserLike of(Account user, LikeTargetType targetType, Long targetId) {
        return UserLike.builder()
                .user(user)
                .targetType(targetType)
                .targetId(targetId)
                .build();
    }
}
