package com.chunbaetour.domain.shop.entity;

import com.chunbaetour.domain.common.entity.BaseEntity;
import com.chunbaetour.domain.merchant.entity.MerchantApplication;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상인 가게 엔티티.
 * 관리자가 MerchantApplication을 승인하면 생성된다 (STORY-09).
 */
@Entity
@Table(
        name = "shops",
        uniqueConstraints = @UniqueConstraint(name = "uk_shops_user_id", columnNames = {"user_id"})
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
    }

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
}
