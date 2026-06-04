package com.chunbaetour.domain.banner;

import com.chunbaetour.domain.banner.type.BannerStatus;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 추천 배너 엔티티 (Admin Epic KAN-177 S09, KAN-216).
 *
 * <p>운영자가 등록/관리하는 메인 노출 배너. {@code priority}(오름차순 정렬 키)와 {@code startDate}/{@code endDate}
 * (노출 기간)로 사용자 노출 순서·시점을 통제한다. 본 슬라이스는 admin CRUD 범위만 구현하고, 기간/우선순위를 적용한
 * 사용자 노출 쿼리는 후속 슬라이스 책임(지시문 D 범위 명시).
 *
 * <p>삭제는 soft delete만 지원한다 — {@link #delete()}로 {@link BannerStatus#DELETED} 전이. 운영자 목록은
 * DELETED를 제외하고 ACTIVE/HIDDEN만 노출한다(BannerRepository.searchForAdmin).
 *
 * <p>불변식: title/imageUrl 필수, 노출 기간이 둘 다 지정되면 {@code startDate <= endDate}. partial update
 * ({@link #update}) 후에도 기간 불변식을 재검증해 수정으로 역전 구간이 생기지 않도록 막는다.
 */
@Entity
@Table(
    name = "banners",
    indexes = {
        // 운영자 목록(searchForAdmin)은 status<>DELETED 필터 + (priority ASC, id DESC) 정렬 →
        // (status, priority, id) 복합으로 status 범위 스캔 + 정렬을 인덱스에서 함께 처리해 filesort를 회피한다.
        @Index(name = "idx_banners_status_priority_id", columnList = "status, priority, id")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Banner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    /** 배너 이미지 URL (필수) */
    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    /** 클릭 시 이동 링크 (선택) */
    @Column(name = "link_url", length = 500)
    private String linkUrl;

    /** 노출 우선순위 — 작을수록 먼저 노출(오름차순 정렬 키). 기본 0. */
    @Column(nullable = false)
    private int priority = 0;

    /** 노출 시작일 (선택, null이면 시작 제한 없음) */
    @Column(name = "start_date")
    private LocalDate startDate;

    /** 노출 종료일 (선택, null이면 종료 제한 없음) */
    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BannerStatus status = BannerStatus.ACTIVE;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private Banner(String title, String imageUrl, String linkUrl, Integer priority,
                   LocalDate startDate, LocalDate endDate) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("배너 제목은 필수입니다.");
        }
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new IllegalArgumentException("배너 이미지 URL은 필수입니다.");
        }
        validatePeriod(startDate, endDate);

        this.title = title;
        this.imageUrl = imageUrl;
        this.linkUrl = linkUrl;
        this.priority = priority != null ? priority : 0;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = BannerStatus.ACTIVE;
    }

    // ── 도메인 메서드 ───────────────────────────────────────────────────

    /**
     * 배너 정보 수정 (관리자 사용) — null 필드는 미수정(null-skip partial, Place.update 패턴).
     *
     * <p>startDate/endDate 중 하나만 넘어와도 병합 후 기간 불변식({@code startDate <= endDate})을 재검증한다 —
     * 부분 수정으로 역전 구간이 생기는 것을 막는다.
     */
    public void update(String title, String imageUrl, String linkUrl, Integer priority,
                       LocalDate startDate, LocalDate endDate) {
        if (title != null) this.title = title;
        if (imageUrl != null) this.imageUrl = imageUrl;
        if (linkUrl != null) this.linkUrl = linkUrl;
        if (priority != null) this.priority = priority;
        LocalDate newStart = startDate != null ? startDate : this.startDate;
        LocalDate newEnd = endDate != null ? endDate : this.endDate;
        validatePeriod(newStart, newEnd);
        this.startDate = newStart;
        this.endDate = newEnd;
    }

    /** 관리자: 배너 숨김 처리 */
    public void hide() {
        this.status = BannerStatus.HIDDEN;
    }

    /** 관리자: 배너 삭제 처리(soft delete) */
    public void delete() {
        this.status = BannerStatus.DELETED;
    }

    /** 관리자: 배너 다시 노출 */
    public void activate() {
        this.status = BannerStatus.ACTIVE;
    }

    /** 노출 기간 불변식 — 둘 다 지정된 경우에만 startDate <= endDate 요구. */
    private static void validatePeriod(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("노출 시작일은 종료일보다 늦을 수 없습니다.");
        }
    }
}
