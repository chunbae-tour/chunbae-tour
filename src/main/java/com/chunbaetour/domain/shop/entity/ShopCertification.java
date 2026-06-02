package com.chunbaetour.domain.shop.entity;

import com.chunbaetour.domain.common.entity.BaseEntity;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.shop.type.ShopCertificationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상인 인증 신청 엔티티 (KAN-204, Admin Epic KAN-177 S05).
 *
 * <p>신청 1건의 상태({@link ShopCertificationStatus}) + 사유 + 처리 메타를 보유한다. 운영자가 PENDING 신청을
 * 승인하면 {@link #approve}로 상태를 APPROVED로 바꾸고, 서비스가 같은 트랜잭션에서 {@link Shop#markCertified()}를
 * 호출해 인증 마크를 부여한다(cascade는 서비스 책임).
 *
 * <p>{@code processedAt}은 도메인 메서드 인자로 주입한다 — 서비스가 {@code Clock} 빈으로 산출한 시각을 넘긴다
 * ({@code AdminUserService.suspend}의 {@code LocalDateTime.now(clock)} 주입 패턴 일관). 엔티티가 직접
 * {@code LocalDateTime.now()}를 호출하지 않아 테스트에서 시각 고정이 가능하다.
 *
 * <p>shops 테이블의 {@code is_certified}는 V1 baseline에 이미 존재 → 본 엔티티는 신규 {@code shop_certifications}
 * 테이블에만 매핑하며 shops를 ALTER하지 않는다.
 */
@Entity
@Table(
        name = "shop_certifications",
        indexes = {
                @Index(name = "idx_shop_cert_status", columnList = "status"),
                @Index(name = "idx_shop_cert_shop", columnList = "shop_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShopCertification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ShopCertificationStatus status;

    @Column(name = "submitted_reason", columnDefinition = "TEXT")
    private String submittedReason;

    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

    /** 취소 사유 — APPROVED 인증 취소 시에만 기록 (방향 B). */
    @Column(name = "cancel_reason", columnDefinition = "TEXT")
    private String cancelReason;

    /** 처리 운영자 userId. 미처리(PENDING) 시 null. */
    @Column(name = "processed_by")
    private Long processedBy;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    /** 승인/거절 처리 시각. 미처리 시 null. */
    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Builder
    private ShopCertification(Long shopId, String submittedReason, LocalDateTime submittedAt) {
        this.shopId = shopId;
        this.submittedReason = submittedReason;
        this.submittedAt = submittedAt;
        this.status = ShopCertificationStatus.PENDING;
    }

    /**
     * 상인 인증 신청 생성 (PENDING). 본 슬라이스는 admin 처리 흐름에 집중하므로 신청 생성 endpoint는 범위 밖
     * (테스트/시드용 팩토리).
     */
    public static ShopCertification submit(Long shopId, String submittedReason, LocalDateTime submittedAt) {
        return ShopCertification.builder()
                .shopId(shopId)
                .submittedReason(submittedReason)
                .submittedAt(submittedAt)
                .build();
    }

    /**
     * 인증 승인 — PENDING에서만 가능. PENDING 외 호출 시 409
     * ({@link ErrorCode#SHOP_CERTIFICATION_INVALID_STATUS}). 인증 마크 부여(Shop.markCertified())는 서비스가
     * 같은 트랜잭션에서 cascade한다.
     */
    public void approve(Long adminUserId, LocalDateTime processedAt) {
        if (this.status != ShopCertificationStatus.PENDING) {
            throw new BusinessException(ErrorCode.SHOP_CERTIFICATION_INVALID_STATUS);
        }
        this.status = ShopCertificationStatus.APPROVED;
        this.processedBy = adminUserId;
        this.processedAt = processedAt;
    }

    /**
     * 인증 거절 — PENDING에서만 가능. PENDING 외 호출 시 409. rejectReason을 기록해 상인이 재신청 시 보강 사항을
     * 확인할 수 있게 한다.
     */
    public void reject(Long adminUserId, String reason, LocalDateTime processedAt) {
        if (this.status != ShopCertificationStatus.PENDING) {
            throw new BusinessException(ErrorCode.SHOP_CERTIFICATION_INVALID_STATUS);
        }
        this.status = ShopCertificationStatus.REJECTED;
        this.processedBy = adminUserId;
        this.rejectReason = reason;
        this.processedAt = processedAt;
    }

    /**
     * 인증 취소 — APPROVED에서만 가능 (방향 B). APPROVED 외 호출 시 409
     * ({@link ErrorCode#SHOP_CERTIFICATION_INVALID_STATUS}). 인증 row에 CANCELLED 전이를 기록하고
     * cancelReason/processedBy/processedAt을 남긴다. 가게 인증 마크 회수(Shop.unmarkCertified())는 서비스가
     * 같은 트랜잭션에서 cascade한다.
     */
    public void cancel(Long adminUserId, String reason, LocalDateTime processedAt) {
        if (this.status != ShopCertificationStatus.APPROVED) {
            throw new BusinessException(ErrorCode.SHOP_CERTIFICATION_INVALID_STATUS);
        }
        this.status = ShopCertificationStatus.CANCELLED;
        this.cancelReason = reason;
        this.processedBy = adminUserId;
        this.processedAt = processedAt;
    }
}
