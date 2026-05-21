package com.chunbaetour.domain.payment.entity;

import com.chunbaetour.domain.common.entity.BaseEntity;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.payment.exception.PaymentException;
import com.chunbaetour.domain.payment.type.PaymentMethod;
import com.chunbaetour.domain.payment.type.PaymentOrderStatus;
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
@Table(name = "payment_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentOrder extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_uid", length = 36, nullable = false, unique = true)
    private String orderUid; // 주문번호 UUID

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long amount;

    @Column(name = "idempotency_key", length = 100, nullable = false, unique = true)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod; // 결제수단

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentOrderStatus status;

    @Column(name = "pg_order_id", length = 100)
    private String pgOrderId; // 포트원이 결제 요청 시 발급한 주문 ID (결제창 오픈 전)

    @Column(name = "pg_transaction_id", length = 100)
    private String pgTransactionId; // 결제 완료 후 포트원 웹훅으로 수신한 트랜잭션 ID

    @Builder
    public PaymentOrder(String orderUid, Long userId, Long amount,
                        String idempotencyKey, PaymentMethod paymentMethod,
                        PaymentOrderStatus status, String pgOrderId) {
        this.orderUid = orderUid;
        this.userId = userId;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
        this.paymentMethod = paymentMethod;
        this.status = status;
        this.pgOrderId = pgOrderId;
    }

    public static PaymentOrder create(String orderUid, Long userId, Long amount,
                                      String idempotencyKey, PaymentMethod paymentMethod, String pgOrderId) {
        return PaymentOrder.builder()
                .orderUid(orderUid)
                .userId(userId)
                .amount(amount)
                .idempotencyKey(idempotencyKey)
                .paymentMethod(paymentMethod)
                .status(PaymentOrderStatus.PENDING)
                .pgOrderId(pgOrderId)
                .build();
    }

    public void complete(String pgTransactionId) {
        if (pgTransactionId == null || pgTransactionId.isBlank()) {
                    throw new PaymentException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
        this.status = PaymentOrderStatus.COMPLETED;
        this.pgTransactionId = pgTransactionId;
    }

    public void fail() {
        this.status = PaymentOrderStatus.FAILED;
    }

    /** 환불 승인 완료 후 주문 상태를 CANCELLED로 전이 */
    public void cancel() {
        this.status = PaymentOrderStatus.CANCELLED;
    }
}
