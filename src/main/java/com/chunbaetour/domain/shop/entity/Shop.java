package com.chunbaetour.domain.shop.entity;

import com.chunbaetour.domain.common.entity.BaseEntity;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.merchant.entity.MerchantApplication;
import com.chunbaetour.domain.shop.dto.request.ShopUpdateRequest;
import com.chunbaetour.domain.shop.type.ShopStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상인 가게 엔티티 (STORY-09/10).
 * 관리자가 MerchantApplication을 승인하면 자동 생성 (STORY-09).
 * 상인이 운영시간/소개글 등 직접 수정 가능, 위치(address/lat/lng)는 수정 불가 (STORY-10).
 */
@Entity
@Table(
        name = "shops",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_shops_user_id", columnNames = {"user_id"}),
                // 동일 신청서로 가게 2개 생성 방지 — 동시 승인 race condition 차단
                @UniqueConstraint(name = "uk_shops_application_id", columnNames = {"application_id"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Shop extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "shop_name", nullable = false, length = 50)
    private String shopName;

    @Column(nullable = false, length = 50)
    private String category;

    // 위치 정보 — 수정 불가 (관리자 처리)
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

    // 이미지 URL 배열 — JSON 형식으로 저장
    @Column(name = "image_urls", columnDefinition = "JSON")
    private String imageUrls;

    // 운영시간 — 예: "월~금 09:00-22:00"
    @Column(name = "operating_hours", length = 100)
    private String operatingHours;

    // 휴무일 — 예: "매주 일요일"
    @Column(name = "closed_days", length = 100)
    private String closedDays;

    // 관리자가 부여하는 인증 마크 (상인이 직접 변경 불가)
    @Column(name = "is_certified", nullable = false)
    private boolean isCertified = false;

    // 리뷰 집계 — 리뷰 도메인에서 갱신
    @Column(nullable = false)
    private double rating = 0.0;

    @Column(name = "review_count", nullable = false)
    private int reviewCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShopStatus status;

    // 낙관적 락 — 동시 PATCH 요청 시 last-write-wins 방지, 충돌 시 CONCURRENT_UPDATE(409)
    @Version
    private Long version;

    @Builder
    private Shop(Long userId, Long applicationId, String shopName, String category,
            String address, BigDecimal lat, BigDecimal lng, String phone, String description) {
        this.userId = userId;
        this.applicationId = applicationId;
        this.shopName = shopName;
        this.category = category;
        this.address = address;
        this.lat = lat;
        this.lng = lng;
        this.phone = phone;
        this.description = description;
        this.status = ShopStatus.ACTIVE;
    }

    /** 관리자 상인 승인 시 MerchantApplication으로부터 가게 생성 (STORY-09). */
    public static Shop fromApplication(MerchantApplication application) {
        return Shop.builder()
                .userId(application.getUserId())
                .applicationId(application.getId())
                .shopName(application.getShopName())
                .category(application.getCategory())
                .address(application.getAddress())
                .lat(application.getLat())
                .lng(application.getLng())
                .phone(application.getPhone())
                .description(application.getDescription())
                .build();
    }

    /**
     * 관리자 신고 처리 정지 (status = SUSPENDED).
     * 공개 노출 차단 + 상인 수정 불가 — CLOSED(폐업)와 구분.
     */
    public void hide() {
        if (this.status == ShopStatus.CLOSED) {
            throw new IllegalStateException("폐업한 가게는 정지 처리할 수 없습니다. shopId=" + this.id);
        }
        this.status = ShopStatus.SUSPENDED;
    }

    /**
     * 관리자 가게 상태 직접 변경 — ACTIVE ↔ SUSPENDED 전환.
     * CLOSED 가게 변경 및 CLOSED로 변경은 서비스 레이어에서 사전 차단.
     */
    public void updateStatus(ShopStatus newStatus) {
        this.status = newStatus;
    }

    /**
     * 상인이 수정 가능한 필드 업데이트 (STORY-10).
     * 위치(address/lat/lng)는 관리자 전용이므로 수정 불가.
     * null = 수정 안 함. "" 는 DTO @Size(min=1)로 진입 전 차단됨.
     * SUSPENDED/CLOSED 상태에서 호출 시 SHOP_INACTIVE — 서비스 레이어 검증과 이중 보호.
     */
    public void update(ShopUpdateRequest request) {
        if (this.status != ShopStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.SHOP_INACTIVE);
        }
        if (request.shopName() != null) this.shopName = request.shopName();
        if (request.category() != null) this.category = request.category();
        if (request.phone() != null) this.phone = request.phone();
        if (request.description() != null) this.description = request.description();
        if (request.operatingHours() != null) this.operatingHours = request.operatingHours();
        if (request.closedDays() != null) this.closedDays = request.closedDays();
        if (request.imageUrls() != null) this.imageUrls = request.imageUrls();
    }
}
