package com.chunbaetour.domain.festival.entity;

import com.chunbaetour.domain.festival.type.FestivalStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "festivals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Festival {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 100)
    private String region;

    @Column(nullable = false, length = 255)
    private String location;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal lat;

    @Column(nullable = false, precision = 11, scale = 7)
    private BigDecimal lng;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "thumbnail_url", length = 512)
    private String thumbnailUrl;

    @Column(name = "image_urls", columnDefinition = "JSON")
    private String imageUrls;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FestivalStatus status = FestivalStatus.ACTIVE;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private Festival(String name, String description, String region, String location,
                     BigDecimal lat, BigDecimal lng, LocalDate startDate, LocalDate endDate,
                     String thumbnailUrl, String imageUrls) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("축제 이름은 필수입니다.");
        }
        if (region == null || region.isBlank()) {
            throw new IllegalArgumentException("축제 지역은 필수입니다.");
        }
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("축제 장소는 필수입니다.");
        }
        if (lat == null || lat.compareTo(new BigDecimal("-90")) < 0 || lat.compareTo(new BigDecimal("90")) > 0) {
            throw new IllegalArgumentException("위도는 -90에서 90 사이여야 합니다.");
        }
        if (lng == null || lng.compareTo(new BigDecimal("-180")) < 0 || lng.compareTo(new BigDecimal("180")) > 0) {
            throw new IllegalArgumentException("경도는 -180에서 180 사이여야 합니다.");
        }
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("시작일과 종료일은 필수입니다.");
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("시작일은 종료일 이전이어야 합니다.");
        }

        this.name = name;
        this.description = description;
        this.region = region;
        this.location = location;
        this.lat = lat;
        this.lng = lng;
        this.startDate = startDate;
        this.endDate = endDate;
        this.thumbnailUrl = thumbnailUrl;
        this.imageUrls = imageUrls;
        this.status = FestivalStatus.ACTIVE;
    }
}
