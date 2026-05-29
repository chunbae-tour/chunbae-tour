package com.chunbaetour.domain.shop.entity;

import com.chunbaetour.domain.common.entity.BaseEntity;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.shop.type.AdApplicationStatus;
import com.chunbaetour.domain.shop.type.AdType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ad_applications", indexes = {
        @Index(name = "idx_ad_applications_shop_id", columnList = "shop_id"),
        @Index(name = "idx_ad_applications_shop_id_status", columnList = "shop_id, status"),
        @Index(name = "idx_ad_applications_status_id", columnList = "status, id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdApplication extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ad_type", nullable = false, length = 50)
    private AdType adType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private long cost;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AdApplicationStatus status;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    public static AdApplication create(Long shopId, AdType adType, LocalDate startDate, LocalDate endDate, long cost) {
        AdApplication a = new AdApplication();
        a.shopId = shopId;
        a.adType = adType;
        a.startDate = startDate;
        a.endDate = endDate;
        a.cost = cost;
        a.status = AdApplicationStatus.PENDING;
        return a;
    }

    public void approve() {
        if (this.status != AdApplicationStatus.PENDING) {
            throw new BusinessException(ErrorCode.AD_APPLICATION_INVALID_STATUS);
        }
        this.status = AdApplicationStatus.APPROVED;
    }

    public void reject(String reason) {
        if (this.status != AdApplicationStatus.PENDING) {
            throw new BusinessException(ErrorCode.AD_APPLICATION_INVALID_STATUS);
        }
        this.status = AdApplicationStatus.REJECTED;
        this.rejectReason = reason;
    }

    /** 광고 연장 비용 계산 — 곱셈 먼저로 정수 나눗셈 손실 최소화 (cost × extensionDays / 기간). */
    public long calculateExtensionCost(int extensionDays) {
        long durationDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (durationDays <= 0) {
            throw new BusinessException(ErrorCode.AD_APPLICATION_INVALID_STATUS);
        }
        return Math.ceilDiv(Math.multiplyExact(cost, extensionDays), durationDays);
    }

    /** 광고 기간 연장 — APPROVED 상태 + 아직 만료되지 않은 경우에만 허용. */
    public void extend(int extensionDays, LocalDate today) {
        if (this.status != AdApplicationStatus.APPROVED) {
            throw new BusinessException(ErrorCode.AD_APPLICATION_INVALID_STATUS);
        }
        if (this.endDate.isBefore(today)) {
            throw new BusinessException(ErrorCode.AD_APPLICATION_INVALID_STATUS);
        }
        this.endDate = this.endDate.plusDays(extensionDays);
    }
}
