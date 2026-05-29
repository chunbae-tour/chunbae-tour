package com.chunbaetour.domain.merchant.entity;

import com.chunbaetour.domain.common.entity.BaseEntity;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.merchant.dto.request.MerchantApplyRequest;
import com.chunbaetour.domain.merchant.type.MerchantApplicationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상인 등록 신청 엔티티.
 * USER가 신청 → PENDING, 관리자 검토 후 APPROVED/REJECTED.
 * APPROVED 시 user.role이 MERCHANT로 변경되고 Shop이 생성됨 (STORY-09).
 */
@Entity
@Table(
        name = "merchant_applications",
        indexes = @Index(name = "idx_merchant_applications_user_id", columnList = "user_id"),
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_merchant_active_business_number",
                        columnNames = {"business_number", "active_flag"}
                ),
                @UniqueConstraint(
                        name = "uk_merchant_active_user_id",
                        columnNames = {"user_id", "active_flag"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MerchantApplication extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 가게명 — 승인 후 shops.shop_name으로 복사됨. 최대 50자 (ERD §5.6)
    @Column(name = "shop_name", nullable = false, length = 50)
    private String shopName;

    @Column(name = "business_number", nullable = false, length = 20)
    private String businessNumber;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(nullable = false)
    private String address;

    @Column(precision = 10, scale = 7)
    private BigDecimal lat;

    @Column(precision = 10, scale = 7)
    private BigDecimal lng;

    @Column(length = 20)
    private String phone;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MerchantApplicationStatus status;

    @Column(name = "reject_reason")
    private String rejectReason;

    // MySQL unique index에서 NULL은 서로 다른 값으로 취급 → REJECTED 재신청 허용
    // PENDING/APPROVED: '1', REJECTED: null → unique(business_number, active_flag)으로 동시 INSERT 차단
    @Column(name = "active_flag", length = 1)
    private String activeFlag;

    @Builder
    private MerchantApplication(Long userId, String shopName, String businessNumber,
            String category, String address, BigDecimal lat, BigDecimal lng,
            String phone, String description) {
        this.userId = userId;
        this.shopName = shopName;
        this.businessNumber = businessNumber;
        this.category = category;
        this.address = address;
        this.lat = lat;
        this.lng = lng;
        this.phone = phone;
        this.description = description;
        this.status = MerchantApplicationStatus.PENDING;
        this.activeFlag = "1";
    }

    public static MerchantApplication create(Long userId, MerchantApplyRequest request) {
        return MerchantApplication.builder()
                .userId(userId)
                .shopName(request.shopName())
                .businessNumber(request.businessNumber().replace("-", ""))
                .category(request.category())
                .address(request.address())
                .lat(request.lat())
                .lng(request.lng())
                .phone(request.phone())
                .description(request.description())
                .build();
    }

    /** 관리자 승인 시 상태 전이 (PENDING에서만 허용). activeFlag=null로 초기화해 uk_merchant_active_user_id 제약 해제 → 동일 상인의 추가 가게 신청 허용 */
    public void approve() {
        if (this.status != MerchantApplicationStatus.PENDING) {
            throw new BusinessException(ErrorCode.MERCHANT_APPLICATION_STATUS_INVALID);
        }
        this.status = MerchantApplicationStatus.APPROVED;
        this.activeFlag = null;
    }

    /** 관리자 거절 시 상태 전이 + 거절 사유 저장 (PENDING에서만 허용) */
    public void reject(String rejectReason) {
        if (this.status != MerchantApplicationStatus.PENDING) {
            throw new BusinessException(ErrorCode.MERCHANT_APPLICATION_STATUS_INVALID);
        }
        this.status = MerchantApplicationStatus.REJECTED;
        this.rejectReason = rejectReason;
        this.activeFlag = null;
    }
}
