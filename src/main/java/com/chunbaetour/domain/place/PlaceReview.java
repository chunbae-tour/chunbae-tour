package com.chunbaetour.domain.place;

import com.chunbaetour.domain.auth.Account;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 관광지 리뷰 엔티티.
 *
 * <ul>
 *   <li>1인 1리뷰 정책: DB Generated Column 기반 UNIQUE 제약(uk_place_review_active)과 비관적 락으로 중복 원천 방지 (삭제 후 재작성 지원)</li>
 *   <li>리뷰 내용: 최대 500자</li>
 *   <li>이미지: JSON 배열로 저장, 최대 5개 (서비스 레이어에서 검증)</li>
 *   <li>soft delete: status = DELETED로 처리</li>
 * </ul>
 */
@Entity
@Table(
    name = "place_reviews",
    indexes = {
        @Index(name = "idx_place_reviews_place",   columnList = "place_id"),
        @Index(name = "idx_place_reviews_user",    columnList = "user_id"),
        @Index(name = "idx_place_reviews_created", columnList = "created_at")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class PlaceReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Account author;

    /** 별점: 1~5 (소수점 없음) */
    @Column(nullable = false)
    private int rating;

    /** 리뷰 내용: 최대 500자 */
    @Column(nullable = false, length = 500)
    private String content;

    /** 리뷰 이미지 URL 목록 (JSON 배열, 최대 5개) — 예: ["url1","url2"] */
    @Column(name = "image_urls", columnDefinition = "JSON")
    private String imageUrls;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PlaceReviewStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ── 정적 팩토리 메서드 ──────────────────────────────────────────────

    /**
     * 리뷰 작성 — 유효성 검증 포함.
     *
     * @param place     대상 관광지
     * @param author    작성자
     * @param rating    별점 (1~5)
     * @param content   리뷰 내용 (최대 500자)
     * @param imageUrls JSON 배열 문자열 (nullable, 최대 5개는 서비스 레이어 검증)
     */
    public static PlaceReview create(Place place, Account author, int rating, String content, String imageUrls) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("별점은 1~5 사이여야 합니다.");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("리뷰 내용은 필수입니다.");
        }
        if (content.length() > 500) {
            throw new IllegalArgumentException("리뷰 내용은 최대 500자까지 입력 가능합니다.");
        }
        PlaceReview review = new PlaceReview();
        review.place     = place;
        review.author    = author;
        review.rating    = rating;
        review.content   = content;
        review.imageUrls = imageUrls;
        review.status    = PlaceReviewStatus.ACTIVE;
        return review;
    }

    // ── 도메인 메서드 ───────────────────────────────────────────────────

    /** 작성자 본인 여부 확인 */
    public boolean isOwnedBy(Long accountId) {
        return this.author.getId().equals(accountId);
    }

    /** 작성자 자발 삭제 (soft delete) — 복원 대상 아님. */
    public void delete() {
        this.status = PlaceReviewStatus.DELETED;
    }

    /** 행정 숨김 (신고 처리·자동제재) — 복원 가능. 자발 삭제(DELETED)는 덮어쓰지 않음. */
    public void hide() {
        if (this.status == PlaceReviewStatus.DELETED) {
            return;
        }
        this.status = PlaceReviewStatus.HIDDEN;
    }

    /** 행정 숨김 복구 — HIDDEN 리뷰만 ACTIVE로 되돌린다 (신고 RESTORE/정정 처리용). 자발 삭제(DELETED)는 부활 안 함. */
    public void restore() {
        if (this.status == PlaceReviewStatus.HIDDEN) {
            this.status = PlaceReviewStatus.ACTIVE;
        }
    }
}
