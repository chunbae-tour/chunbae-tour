package com.chunbaetour.domain.place;

import com.chunbaetour.domain.place.type.PlaceCategory;
import com.chunbaetour.domain.place.type.PlaceStatus;
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
import java.time.LocalDateTime;
import org.locationtech.jts.geom.Point;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(
    name = "places",
    indexes = {
        @Index(name = "idx_places_category", columnList = "category"),
        // 운영자 목록(searchForAdmin)은 status<>DELETED 필터 + id DESC 정렬 → (status, id) 복합으로 filesort 회피.
        // 기존 단일 idx_places_status는 본 복합의 leftmost prefix라 중복 → 제거(KAN-209 S07 리뷰 G).
        @Index(name = "idx_places_status_id", columnList = "status, id"),
        // 공간 인덱스는 JPA @Index로 정의할 수 없으므로 DB 마이그레이션(V202606041400)에서 직접 생성
        @Index(name = "idx_places_name",     columnList = "name")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Place {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PlaceCategory category;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 255)
    private String address;

    /** 공간 인덱싱을 위한 JTS Point 객체 (경도, 위도) SRID 4326 */
    @Column(nullable = false, columnDefinition = "POINT SRID 4326")
    private Point location;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    /** JSON 배열로 저장 (예: ["url1","url2"]) */
    @Column(name = "image_urls", columnDefinition = "JSON")
    private String imageUrls;

    @Column(name = "operating_hours", length = 100)
    private String operatingHours;

    @Column(name = "closed_days", length = 100)
    private String closedDays;

    @Column(length = 20)
    private String phone;

    @Column(name = "admission_fee", length = 50)
    private String admissionFee;

    /** 평균 별점 — 리뷰 작성/삭제 시 재계산 (소수점 1자리를 정수로 저장. 예: 4.5 -> 45) */
    @Column(nullable = false)
    private int rating = 0;

    @Column(name = "review_count", nullable = false)
    private int reviewCount = 0;

    /** likeCount는 Redis INCR/DECR 후 배치로 DB 동기화 */
    @Column(name = "like_count", nullable = false)
    private int likeCount = 0;

    /** viewCount는 Redis INCR 후 배치로 DB 동기화 */
    @Column(name = "view_count", nullable = false)
    private int viewCount = 0;

    /** JSON 배열로 저장 (예: ["한옥","야간개장"]) */
    @Column(columnDefinition = "JSON")
    private String tags;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlaceStatus status = PlaceStatus.ACTIVE;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private Place(String name, PlaceCategory category, String description,
                  String address, Point location, java.math.BigDecimal lat, java.math.BigDecimal lng,
                  String thumbnailUrl, String imageUrls, String operatingHours,
                  String closedDays, String phone, String admissionFee, String tags) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("이름은 필수입니다.");
        }
        if (category == null) {
            throw new IllegalArgumentException("카테고리는 필수입니다.");
        }
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("주소는 필수입니다.");
        }
        
        Point finalLocation = location;
        if (finalLocation != null) {
            finalLocation.setSRID(4326);
        } else if (lat != null && lng != null) {
            org.locationtech.jts.geom.GeometryFactory gf = new org.locationtech.jts.geom.GeometryFactory();
            finalLocation = gf.createPoint(new org.locationtech.jts.geom.Coordinate(lng.doubleValue(), lat.doubleValue()));
            finalLocation.setSRID(4326);
        }
        
        if (finalLocation == null) {
            throw new IllegalArgumentException("좌표 위치는 필수입니다.");
        }
        
        this.name = name;
        this.category = category;
        this.description = description;
        this.address = address;
        this.location = finalLocation;
        this.thumbnailUrl = thumbnailUrl;
        this.imageUrls = imageUrls;
        this.operatingHours = operatingHours;
        this.closedDays = closedDays;
        this.phone = phone;
        this.admissionFee = admissionFee;
        this.tags = tags;
        this.status = PlaceStatus.ACTIVE;
    }

    // ── 도메인 메서드 ───────────────────────────────────────────────────

    /** 관광지 상세 정보 수정 (관리자 사용) */
    public void update(String name, String description, String address,
                       String operatingHours, String closedDays,
                       String phone, String admissionFee, String tags) {
        if (name != null) this.name = name;
        if (description != null) this.description = description;
        if (address != null) this.address = address;
        if (operatingHours != null) this.operatingHours = operatingHours;
        if (closedDays != null) this.closedDays = closedDays;
        if (phone != null) this.phone = phone;
        if (admissionFee != null) this.admissionFee = admissionFee;
        if (tags != null) this.tags = tags;
    }

    /** 평균 별점 재계산 (리뷰 추가/삭제 시 호출) */
    public void recalculateRating(double newTotalScore, int newReviewCount) {
        if (newTotalScore < 0 || newReviewCount < 0) {
            throw new IllegalArgumentException("리뷰 점수나 개수는 음수가 될 수 없습니다.");
        }
        this.reviewCount = newReviewCount;
        this.rating = newReviewCount == 0 ? 0 : (int) Math.round((newTotalScore / newReviewCount) * 10);
    }

    /** 관리자: 관광지 숨김 처리 */
    public void hide() {
        this.status = PlaceStatus.HIDDEN;
    }

    /** 관리자: 관광지 삭제 처리 */
    public void delete() {
        this.status = PlaceStatus.DELETED;
    }

    /** 관리자: 관광지 다시 노출 */
    public void activate() {
        this.status = PlaceStatus.ACTIVE;
    }

    // ── 하위 호환성 헬퍼 ────────────────────────────────────────────────
    public java.math.BigDecimal getLat() {
        return location != null ? java.math.BigDecimal.valueOf(location.getY()) : null;
    }

    public java.math.BigDecimal getLng() {
        return location != null ? java.math.BigDecimal.valueOf(location.getX()) : null;
    }

    /** UI 표시용 float 평점 반환 (예: 45 -> 4.5f) */
    public float getDisplayRating() {
        return rating / 10.0f;
    }
}
