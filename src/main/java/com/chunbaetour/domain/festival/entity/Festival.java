package com.chunbaetour.domain.festival.entity;

import com.chunbaetour.domain.common.entity.BaseEntity;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.festival.type.FestivalCategory;
import com.chunbaetour.domain.festival.type.FestivalSource;
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FestivalSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FestivalCategory category;

    @Column(name = "external_id", length = 100, unique = true)
    private String externalId;

    // ── 팩토리 메서드 ──────────────────────────────────────────────────────

    public static Festival create(String name, String description, String region,
            String address, LocalDate startDate, LocalDate endDate,
            String imageUrl, String relatedUrl, FestivalStatus status) {
        FestivalStatus resolvedStatus = status != null ? status : FestivalStatus.ACTIVE;
        if (resolvedStatus == FestivalStatus.DELETED) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        validateInvariant(name, region, address, startDate, endDate, resolvedStatus);
        Festival f = new Festival();
        f.name = name;
        f.description = description;
        f.region = region;
        f.address = address;
        f.startDate = startDate;
        f.endDate = endDate;
        f.imageUrl = imageUrl;
        f.relatedUrl = relatedUrl;
        f.status = resolvedStatus;
        f.source = FestivalSource.MANUAL;
        f.category = FestivalCategory.FESTIVAL;
        return f;
    }

    public static Festival createFromApi(String externalId, String name, String region,
            String address, LocalDate startDate, LocalDate endDate, String imageUrl) {
        validateInvariant(name, region, address, startDate, endDate, FestivalStatus.ACTIVE);
        Festival f = new Festival();
        f.externalId = externalId;
        f.name = name;
        f.region = region;
        f.address = address;
        f.startDate = startDate;
        f.endDate = endDate;
        f.imageUrl = imageUrl;
        f.status = FestivalStatus.ACTIVE;
        f.source = FestivalSource.API_FETCH;
        f.category = FestivalCategory.FESTIVAL;
        return f;
    }

    // ── 도메인 메서드 ─────────────────────────────────────────────────────

    public void update(String name, String description, String region,
            String address, LocalDate startDate, LocalDate endDate,
            String imageUrl, String relatedUrl, FestivalStatus status) {
        if (status == FestivalStatus.DELETED) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        validateInvariant(name, region, address, startDate, endDate, status);
        this.name = name;
        this.description = description;
        this.region = region;
        this.address = address;
        this.startDate = startDate;
        this.endDate = endDate;
        this.imageUrl = imageUrl;
        this.relatedUrl = relatedUrl;
        this.status = status;
    }

    public void updateFromApi(String name, String region, String address,
            LocalDate startDate, LocalDate endDate, String imageUrl) {
        validateInvariant(name, region, address, startDate, endDate, this.status);
        this.name = name;
        this.region = region;
        this.address = address;
        this.startDate = startDate;
        this.endDate = endDate;
        this.imageUrl = imageUrl;
        // source, externalId, description, relatedUrl, status, category 유지
    }

    private static void validateInvariant(String name, String region, String address,
            LocalDate startDate, LocalDate endDate, FestivalStatus status) {
        if (name == null || name.isBlank()
                || region == null || region.isBlank()
                || address == null || address.isBlank()
                || startDate == null || endDate == null
                || startDate.isAfter(endDate)
                || status == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
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
