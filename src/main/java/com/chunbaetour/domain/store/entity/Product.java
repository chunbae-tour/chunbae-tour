package com.chunbaetour.domain.store.entity;

import com.chunbaetour.domain.common.entity.BaseEntity;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.store.type.ProductCategory;
import com.chunbaetour.domain.store.type.ProductStatus;
import com.chunbaetour.domain.store.type.RedemptionScope;
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

    @Enumerated(EnumType.STRING)
    // DB products.category는 V1 baseline부터 varchar(50) — 컬럼 정의와 길이를 맞춘다 (KAN-302 리뷰, DDL 변경 아님)
    @Column(nullable = false, length = 50)
    private ProductCategory category;

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

    /**
     * 사용처(redemption) 범위 — QR 사용 처리 시 사용 가능한 검증자/가게를 제한하는 기준.
     * 현재 모든 스토어 상품은 {@link RedemptionScope#GLOBAL}(전 가맹점 공통). 가게/파트너/행사 한정은
     * 추후 redemption 도메인 확장에서 도입(hanes/미구현API.md 참조).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "redemption_scope", nullable = false, length = 20)
    private RedemptionScope redemptionScope;

    /** 관리자 상품 등록 팩토리 메서드 — 초기 status = ON_SALE */
    public static Product create(String name, String description, ProductCategory category,
                                 long price, Long originalPrice, int stock,
                                 String imageUrlsJson, String merchantName,
                                 Integer validityDays, int maxPerPerson) {
        // invariant: ON_SALE로 생성하므로 stock > 0 필수
        if (stock <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return Product.builder()
                .name(name).description(description).category(category)
                .price(price).originalPrice(originalPrice)
                .stock(stock).originalStock(stock)
                .imageUrls(imageUrlsJson).merchantName(merchantName)
                .validityDays(validityDays).status(ProductStatus.ON_SALE)
                .maxPerPerson(maxPerPerson).build();
    }

    /**
     * 관리자 상품 수정 — null 필드는 기존 값 유지.
     * stock 수정 시 originalStock도 항상 갱신 — stock 감소 정정(과다 입고 수정) 시 허위 soldCount 방지.
     * status 미명시 시 stock 기준 자동 전환, 명시 시 명시값 우선.
     * 불변식(invariant): status=ON_SALE + stock=0 조합 금지.
     */
    public void adminUpdate(String name, String description, ProductCategory category,
                            Long price, Long originalPrice, Integer stock,
                            String imageUrlsJson, String merchantName,
                            Integer validityDays, Integer maxPerPerson, ProductStatus status) {
        if (name != null) this.name = name;
        if (description != null) this.description = description;
        if (category != null) this.category = category;
        if (price != null) this.price = price;
        if (originalPrice != null) this.originalPrice = originalPrice;
        // price·originalPrice 독립 갱신 후 최종 상태 검증 — 순서 무관하게 불변식 보장
        if (this.originalPrice != null && this.originalPrice < this.price) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (stock != null) {
            // 증가·감소 모두 originalStock 갱신 — soldCount 기준점을 마지막 관리자 조정 시점으로 리셋
            this.originalStock = stock;
            this.stock = stock;
            if (status == null) {
                if (this.stock > 0 && this.status == ProductStatus.SOLD_OUT) {
                    this.status = ProductStatus.ON_SALE;
                } else if (this.stock == 0 && this.status == ProductStatus.ON_SALE) {
                    this.status = ProductStatus.SOLD_OUT;
                }
            }
        }
        if (imageUrlsJson != null) this.imageUrls = imageUrlsJson;
        if (merchantName != null) this.merchantName = merchantName;
        if (validityDays != null) this.validityDays = validityDays;
        if (maxPerPerson != null) this.maxPerPerson = maxPerPerson;
        // status 적용 우선순위: 명시값 > stock 기반 자동 전환.
        // 명시값이 있더라도 invariant는 최종 상태로 항상 검증 — 예: stock=0 + status=ON_SALE 명시 시 여기서 throw.
        if (status != null) this.status = status;

        // invariant: ON_SALE 상태에서 재고 0 금지
        if (this.status == ProductStatus.ON_SALE && this.stock == 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
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
    private Product(String name, String description, ProductCategory category, long price,
                    Long originalPrice, int stock, int originalStock, String imageUrls,
                    String merchantName, Integer validityDays, ProductStatus status,
                    int maxPerPerson, RedemptionScope redemptionScope) {
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
        // 사용처 범위 기본값 GLOBAL — 빌더에서 미지정 시에도 NOT NULL 불변식 보장(현 스토어 상품은 전부 공통권).
        this.redemptionScope = redemptionScope != null ? redemptionScope : RedemptionScope.GLOBAL;
    }
}
