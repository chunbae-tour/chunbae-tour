package com.chunbaetour.domain.store.entity;

import com.chunbaetour.domain.common.entity.BaseEntity;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.store.dto.request.AdminProductCreateRequest;
import com.chunbaetour.domain.store.dto.request.AdminProductUpdateRequest;
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

    /** 1인당 최대 구매 수량 — 이벤트·상품마다 다른 구매 한도 적용 */
    @Column(nullable = false)
    private int maxPerPerson;

    /** 관리자 상품 등록 팩토리 메서드 — 초기 status = ON_SALE */
    public static Product create(AdminProductCreateRequest req) {
        return Product.builder()
                .name(req.name())
                .description(req.description())
                .category(req.category())
                .price(req.price())
                .originalPrice(req.originalPrice())
                .stock(req.stock())
                .originalStock(req.stock())
                .imageUrls(req.imageUrls())
                .merchantName(req.merchantName())
                .validityDays(req.validityDays())
                .status(ProductStatus.ON_SALE)
                .maxPerPerson(req.maxPerPerson())
                .build();
    }

    /**
     * 관리자 상품 수정 — null 필드는 기존 값 유지.
     * stock 증가 + SOLD_OUT 상태면 ON_SALE 자동 복구.
     * status 명시 시 직접 전환 (ON_SALE·SOLD_OUT·HIDDEN).
     */
    public void adminUpdate(AdminProductUpdateRequest req) {
        if (req.name() != null) this.name = req.name();
        if (req.description() != null) this.description = req.description();
        if (req.category() != null) this.category = req.category();
        if (req.price() != null) this.price = req.price();
        if (req.originalPrice() != null) this.originalPrice = req.originalPrice();
        if (req.stock() != null) {
            this.stock = req.stock();
            // status 명시 없고 재고 추가됐는데 SOLD_OUT이면 ON_SALE 복구
            if (req.status() == null && this.stock > 0 && this.status == ProductStatus.SOLD_OUT) {
                this.status = ProductStatus.ON_SALE;
            }
        }
        if (req.imageUrls() != null) this.imageUrls = req.imageUrls();
        if (req.merchantName() != null) this.merchantName = req.merchantName();
        if (req.validityDays() != null) this.validityDays = req.validityDays();
        if (req.maxPerPerson() != null) this.maxPerPerson = req.maxPerPerson();
        if (req.status() != null) this.status = req.status();
    }

    /** 관리자 상품 삭제 — status = HIDDEN (soft delete, 공개 조회에서 제외) */
    public void softDelete() {
        this.status = ProductStatus.HIDDEN;
    }

    /** 구매 시 재고 차감 — 재고 소진 시 SOLD_OUT 자동 전환 */
    public void decreaseStock(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ErrorCode.INVALID_PURCHASE_QUANTITY);
        }
        if (this.stock < quantity) {
            throw new BusinessException(ErrorCode.PRODUCT_SOLD_OUT);
        }
        this.stock -= quantity;
        if (this.stock == 0) {
            this.status = ProductStatus.SOLD_OUT;
        }
    }

    @Builder
    private Product(String name, String description, String category, long price,
                    Long originalPrice, int stock, int originalStock, String imageUrls,
                    String merchantName, Integer validityDays, ProductStatus status,
                    int maxPerPerson) {
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
        this.maxPerPerson = maxPerPerson;
    }
}
