package com.chunbaetour.domain.festival.entity;

import com.chunbaetour.domain.common.entity.BaseEntity;
import com.chunbaetour.domain.festival.type.FestivalStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 축제 엔티티.
 * progressStatus는 startDate/endDate 기준 동적 계산 — DB 저장 X.
 */
@Entity
@Table(name = "festivals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Festival extends BaseEntity {

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
    private String address;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "image_url", length = 512)
    private String imageUrl;

    @Column(name = "related_url", length = 512)
    private String relatedUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FestivalStatus status;

    // ── 팩토리 메서드 ──────────────────────────────────────────────────────

    public static Festival create(String name, String description, String region,
            String address, LocalDate startDate, LocalDate endDate,
            String imageUrl, String relatedUrl, FestivalStatus status) {
        Festival f = new Festival();
        f.name = name;
        f.description = description;
        f.region = region;
        f.address = address;
        f.startDate = startDate;
        f.endDate = endDate;
        f.imageUrl = imageUrl;
        f.relatedUrl = relatedUrl;
        f.status = status != null ? status : FestivalStatus.ACTIVE;
        return f;
    }

    // ── 도메인 메서드 ─────────────────────────────────────────────────────

    public void update(String name, String description, String region,
            String address, LocalDate startDate, LocalDate endDate,
            String imageUrl, String relatedUrl, FestivalStatus status) {
        this.name = name;
        this.description = description;
        this.region = region;
        this.address = address;
        this.startDate = startDate;
        this.endDate = endDate;
        this.imageUrl = imageUrl;
        this.relatedUrl = relatedUrl;
        if (status != null) {
            this.status = status;
        }
    }

    /** Soft delete — status = DELETED. */
    public void delete() {
        this.status = FestivalStatus.DELETED;
    }

    /** 활성 상태 여부. */
    public boolean isActive() {
        return status == FestivalStatus.ACTIVE;
    }
}
