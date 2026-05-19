package com.chunbaetour.domain.payment.entity;

import com.chunbaetour.domain.common.entity.BaseEntity;
import com.chunbaetour.domain.payment.type.PaymentOrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentOrder extends BaseEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentOrderStatus status;

    @Column(name = "pg_order_id", length = 100)
    private String pgOrderId;

    public static PaymentOrder create(String id, Long userId, Long amount, String pgOrderId) {
        PaymentOrder order = new PaymentOrder();
        order.id = id;
        order.userId = userId;
        order.amount = amount;
        order.status = PaymentOrderStatus.PENDING;
        order.pgOrderId = pgOrderId;
        return order;
    }

    public void complete() {
        this.status = PaymentOrderStatus.COMPLETED;
    }

    public void fail() {
        this.status = PaymentOrderStatus.FAILED;
    }
}
