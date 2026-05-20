package com.chunbaetour.domain.community.companion.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "companion_posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class CompanionPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(name = "place_name", nullable = false, length = 100)
    private String placeName;

    @Column(length = 50)
    private String region;

    @Column(name = "meeting_date", nullable = false)
    private LocalDate meetingDate;

    @Column(name = "max_members", nullable = false)
    private int maxMembers;

    @Column(name = "current_members", nullable = false)
    private int currentMembers;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CompanionPostStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public static CompanionPost create(
            Long authorId, String title, String content,
            Long placeId, String placeName, String region,
            LocalDate meetingDate, int maxMembers) {
        if (maxMembers < 2) {
            throw new IllegalArgumentException("maxMembers must be at least 2");
        }
        CompanionPost post = new CompanionPost();
        post.authorId = authorId;
        post.title = title;
        post.content = content;
        post.placeId = placeId;
        post.placeName = placeName;
        post.region = region;
        post.meetingDate = meetingDate;
        post.maxMembers = maxMembers;
        post.currentMembers = 1;
        post.status = CompanionPostStatus.ACTIVE;
        return post;
    }

    public void update(String title, String content, Long placeId, String placeName,
                       String region, LocalDate meetingDate, int maxMembers) {
        if (title != null) this.title = title;
        if (content != null) this.content = content;
        if (placeId != null) this.placeId = placeId;
        if (placeName != null) this.placeName = placeName;
        if (region != null) this.region = region;
        if (meetingDate != null) this.meetingDate = meetingDate;
        if (maxMembers > 0) {
            if (maxMembers < this.currentMembers) {
                throw new IllegalArgumentException("maxMembers must be >= currentMembers");
            }
            this.maxMembers = maxMembers;
        }
    }

    public void delete() {
        this.status = CompanionPostStatus.DELETED;
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isOwnedBy(Long accountId) {
        return this.authorId.equals(accountId);
    }
}
