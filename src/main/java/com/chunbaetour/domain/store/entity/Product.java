package com.chunbaetour.domain.store.entity;

import com.chunbaetour.domain.common.entity.BaseEntity;
import com.chunbaetour.domain.store.type.ProductStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(nullable = false)
    private long price;

    /** 상품 원가/권장 소비자가 (쿠폰 액면가 등) — null 허용 */
    @Column
    private Long originalPrice;

    @Column(nullable = false)
    private int stock;

    @Column(nullable = false)
    private int originalStock;

    // 이미지 URL 배열 — JSON 형식으로 저장
    @Column(columnDefinition = "JSON")
    private String imageUrls;

    @Column(length = 100)
    private String merchantName;

    @Column
    private Integer validityDays;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status;

    /** 구매 시 재고 차감 — 재고 소진 시 SOLD_OUT 자동 전환 */
    public void decreaseStock(int quantity) {
        if (this.stock < quantity) {
            throw new com.chunbaetour.domain.common.error.BusinessException(
                    com.chunbaetour.domain.common.error.ErrorCode.PRODUCT_SOLD_OUT);
        }
        this.stock -= quantity;
        if (this.stock == 0) {
            this.status = ProductStatus.SOLD_OUT;
        }
    }

    @Builder
    private Product(String name, String description, String category, long price,
                    Long originalPrice, int stock, int originalStock, String imageUrls,
                    String merchantName, Integer validityDays, ProductStatus status) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.price = price;
        this.originalPrice = originalPrice;
        this.stock = stock;
        this.originalStock = originalStock;
        this.imageUrls = imageUrls;
        this.merchantName = merchantName;
        this.validityDays = validityDays;
        this.status = status;
    }
}
