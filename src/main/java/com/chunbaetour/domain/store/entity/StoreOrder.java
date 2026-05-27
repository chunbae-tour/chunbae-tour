package com.chunbaetour.domain.store.entity;

import com.chunbaetour.domain.common.entity.BaseEntity;
import com.chunbaetour.domain.store.type.StoreOrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "store_orders", indexes = @Index(name = "idx_store_order_cursor", columnList = "user_id, id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreOrder extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long productId;

    /** 구매 시점 상품명 스냅샷 — 이후 상품 이름 변경에 영향받지 않음 */
    @Column(nullable = false, length = 100)
    private String productName;

    /** 구매 시점 단가 스냅샷 */
    @Column(nullable = false)
    private long productPrice;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private long totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StoreOrderStatus status;

    public static StoreOrder create(Long userId, Product product, int quantity, long totalPrice) {
        StoreOrder order = new StoreOrder();
        order.userId = userId;
        order.productId = product.getId();
        order.productName = product.getName();
        order.productPrice = product.getPrice();
        order.quantity = quantity;
        order.totalPrice = totalPrice;
        order.status = StoreOrderStatus.COMPLETED;
        return order;
    }
}
