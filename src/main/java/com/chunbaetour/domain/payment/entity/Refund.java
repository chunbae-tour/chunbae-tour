package com.chunbaetour.domain.payment.entity;

import com.chunbaetour.domain.common.entity.BaseEntity;
import com.chunbaetour.domain.payment.type.RefundStatus;
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

/**
 * 환불 요청 엔티티.
 * 유저가 환불을 요청하면 PENDING으로 생성, 관리자가 APPROVED/REJECTED로 변경.
 * 실제 PG 환불은 STORY-07(관리자 승인) 시점에 발생.
 */
@Entity
@Table(name = "refunds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Refund extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 환불 대상 PaymentOrder DB id
    @Column(name = "payment_order_id", nullable = false)
    private Long paymentOrderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 환불 요청 금액 (= PaymentOrder.amount, 전액 환불만 지원)
    @Column(nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefundStatus status;

    // 환불 사유 (선택 입력)
    @Column
    private String reason;

    @Builder
    private Refund(Long paymentOrderId, Long userId, Long amount, String reason) {
        this.paymentOrderId = paymentOrderId;
        this.userId = userId;
        this.amount = amount;
        this.status = RefundStatus.PENDING;
        this.reason = reason;
    }

    public static Refund create(Long paymentOrderId, Long userId, Long amount, String reason) {
        return Refund.builder()
                .paymentOrderId(paymentOrderId)
                .userId(userId)
                .amount(amount)
                .reason(reason)
                .build();
    }

    /** 관리자 승인 시 상태 전이 */
    public void approve() {
        this.status = RefundStatus.APPROVED;
    }

    /** 관리자 거절 시 상태 전이 */
    public void reject() {
        this.status = RefundStatus.REJECTED;
    }

    /** 사용자 취소 시 상태 전이 */
    public void cancel() {
        this.status = RefundStatus.CANCELLED;
    }
}
