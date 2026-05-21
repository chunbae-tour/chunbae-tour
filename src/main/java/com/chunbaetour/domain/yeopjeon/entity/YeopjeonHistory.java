package com.chunbaetour.domain.yeopjeon.entity;

import com.chunbaetour.domain.common.entity.BaseEntity;
import com.chunbaetour.domain.yeopjeon.type.YeopjeonHistoryType;
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
@Table(name = "yeopjeon_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class YeopjeonHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private YeopjeonHistoryType type;

    @Column(nullable = false)
    private Long amount;

    @Column(name = "balance_snapshot", nullable = false)
    private Long balanceSnapshot;

    @Column(name = "payment_order_id")
    private Long paymentOrderId;

    @Builder
    private YeopjeonHistory(Long userId, YeopjeonHistoryType type, Long amount,
                            Long balanceSnapshot, Long paymentOrderId) {
        this.userId = userId;
        this.type = type;
        this.amount = amount;
        this.balanceSnapshot = balanceSnapshot;
        this.paymentOrderId = paymentOrderId;
    }

    public static YeopjeonHistory ofCharge(Long userId, Long amount, Long balanceSnapshot, Long paymentOrderId) {
        return YeopjeonHistory.builder()
                .userId(userId)
                .type(YeopjeonHistoryType.CHARGE)
                .amount(amount)
                .balanceSnapshot(balanceSnapshot)
                .paymentOrderId(paymentOrderId)
                .build();
    }

    /** 환불 이력 전용 팩토리. 관리자 승인 후 차감된 엽전 이력 기록. */
    public static YeopjeonHistory ofRefund(Long userId, Long amount, Long balanceSnapshot, Long paymentOrderId) {
        return YeopjeonHistory.builder()
                .userId(userId)
                .type(YeopjeonHistoryType.REFUND)
                .amount(amount)
                .balanceSnapshot(balanceSnapshot)
                .paymentOrderId(paymentOrderId)
                .build();
    }
}
