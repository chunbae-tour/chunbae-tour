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
import java.util.UUID;
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

    // 소속 장소 — 선택 연결. null이면 장소 미연결 가게 (KAN-217)
    @Column(name = "place_id")
    private Long placeId;

    // 소속 전통시장 — 선택 연결. null이면 시장 미연결 가게 (KAN-220)
    @Column(name = "traditional_market_id")
    private Long traditionalMarketId;

    // 낙관적 락 — 동시 PATCH 요청 시 last-write-wins 방지, 충돌 시 CONCURRENT_UPDATE(409)
    @Version
    private Long version;

    // QR 결제 payload에 포함되는 회전 nonce (KAN-253). 재발급 시 새 UUID로 교체 → 옛 QR 무효화.
    // QR 결제 요청 생성 시 payload의 nonce가 현재 값과 일치해야 결제 가능.
    @Column(name = "qr_nonce", nullable = false, length = 36)
    private String qrNonce;

    @Builder
    private Shop(Long userId, Long applicationId, String shopName, String category,
            String address, BigDecimal lat, BigDecimal lng, String phone, String description,
            Long placeId, Long traditionalMarketId) {
        this.userId = userId;
        this.applicationId = applicationId;
        this.shopName = shopName;
        this.category = category;
        this.address = address;
        this.lat = lat;
        this.lng = lng;
        this.phone = phone;
        this.description = description;
        this.placeId = placeId;
        this.traditionalMarketId = traditionalMarketId;
        this.status = ShopStatus.ACTIVE;
        // 가게 생성 시 QR nonce 초기 발급 — 이후 reissueQr()로 회전
        this.qrNonce = UUID.randomUUID().toString();
    }

    /** 관리자 상인 승인 시 MerchantApplication + placeId로 가게 생성 (STORY-09, KAN-217). */
    public static Shop fromApplication(MerchantApplication application, Long placeId) {
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
                .placeId(placeId)
                .build();
    }

    /**
     * 관리자 신고 처리 정지 (status = SUSPENDED).
     * 공개 노출 차단 + 상인 수정 불가 — CLOSED(폐업)와 구분.
     */
    public void hide() {
        if (this.status == ShopStatus.CLOSED) {
            throw new BusinessException(ErrorCode.SHOP_INACTIVE);
        }
        this.status = ShopStatus.SUSPENDED;
    }

    /**
     * 관리자 노출 복구 (SUSPENDED → ACTIVE). hide()의 대칭 메서드 (KAN-203 Admin S04).
     * CLOSED(폐업) 가게는 복구 불가 — hide()와 동일하게 SHOP_INACTIVE.
     * 이미 ACTIVE면 멱등(no-op) — 재호출해도 ACTIVE 유지.
     */
    public void activate() {
        if (this.status == ShopStatus.CLOSED) {
            throw new BusinessException(ErrorCode.SHOP_INACTIVE);
        }
        this.status = ShopStatus.ACTIVE;
    }

    /**
     * 관리자 전용 partial update — status 무관 수정 가능 (KAN-203 Admin S04).
     * 상인용 {@link #update(ShopUpdateRequest)}는 status=ACTIVE 가드가 있어 SUSPENDED 가게를 못 고치므로 별도.
     * null = 미수정 (KAN-127 Account.updateProfile 패턴). "" 는 DTO @Size(min=1)로 진입 전 차단됨.
     * status 전이는 본 메서드가 아니라 서비스에서 hide()/activate()로 처리 — 여기서 직접 세팅 금지.
     */
    public void adminUpdate(String description, String phone, String operatingHours) {
        if (description != null) this.description = description;
        if (phone != null) this.phone = phone;
        if (operatingHours != null) this.operatingHours = operatingHours;
    }

    /**
     * 관리자 장소 연결 (KAN-217). placeId=null 허용 — 연결 해제.
     * 검증(Place 존재 여부)은 서비스 레이어에서 처리.
     */
    public void linkPlace(Long placeId) {
        this.placeId = placeId;
    }

    /**
     * QR 재발급 (KAN-253). nonce를 새 UUID로 교체 → 기존 QR payload의 nonce가 더 이상 일치하지 않아
     * 옛 QR로는 결제 요청이 거절된다. 분실·도용 의심 시 상인이 호출.
     */
    public void reissueQr() {
        this.qrNonce = UUID.randomUUID().toString();
    }

    /**
     * 인증 마크 부여 (KAN-204, Admin S05). 운영자가 인증 신청을 승인할 때 서비스가 호출 —
     * {@code ShopCertification.approve()}와 같은 트랜잭션에서 cascade.
     */
    public void markCertified() {
        this.isCertified = true;
    }

    /**
     * 인증 마크 회수 (KAN-204, Admin S05). 운영자가 인증 기준 미충족 발견 시 인증을 취소할 때 서비스가 호출.
     */
    public void unmarkCertified() {
        this.isCertified = false;
    }

    /**
     * 상인 직접 상태 전환 (KAN-213). ACTIVE ↔ CLOSED만 허용.
     * SUSPENDED 요청 시 SHOP_STATUS_FORBIDDEN — 관리자 전용 상태.
     * SUSPENDED 가게는 상인이 직접 변경 불가 — 관리자 처리 필요.
     */
    public void merchantChangeStatus(ShopStatus newStatus) {
        // SUSPENDED 전환 요청 차단 — 관리자 전용
        if (newStatus == ShopStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.SHOP_STATUS_FORBIDDEN);
        }
        // 현재 SUSPENDED 상태에서는 상인이 변경 불가
        if (this.status == ShopStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.SHOP_INACTIVE);
        }
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
