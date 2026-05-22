package com.chunbaetour.domain.merchant.entity;

import com.chunbaetour.domain.common.entity.BaseEntity;
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
        indexes = @Index(name = "idx_merchant_applications_user_id", columnList = "user_id")
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
    }

    public static MerchantApplication create(Long userId, MerchantApplyRequest request) {
        return MerchantApplication.builder()
                .userId(userId)
                .shopName(request.shopName())
                .businessNumber(request.businessNumber())
                .category(request.category())
                .address(request.address())
                .lat(request.lat())
                .lng(request.lng())
                .phone(request.phone())
                .description(request.description())
                .build();
    }

    // TODO [STORY-09]: approve() 호출 후 서비스 레이어에서 다음 두 작업을 원자적으로 수행해야 한다.
    //   1. user.role을 USER → MERCHANT로 변경 (UserRepository.findByIdWithLock 후 user.promoteToMerchant())
    //   2. Shop 엔티티 생성 (shopName, address, lat, lng, phone, description 이 엔티티에서 복사)
    /** 관리자 승인 시 상태 전이 */
    public void approve() {
        this.status = MerchantApplicationStatus.APPROVED;
    }

    /** 관리자 거절 시 상태 전이 + 거절 사유 저장 */
    public void reject(String rejectReason) {
        this.status = MerchantApplicationStatus.REJECTED;
        this.rejectReason = rejectReason;
    }
}
