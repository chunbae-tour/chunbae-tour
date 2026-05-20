package com.chunbaetour.domain.yeopjeon.entity;

import com.chunbaetour.domain.common.entity.BaseEntity;
import com.chunbaetour.domain.yeopjeon.type.YeopjeonHistoryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "yeopjeon_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class YeopjeonHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "payment_order_id")
    private Long paymentOrderId;

    @Column(name = "shop_id")
    private Long shopId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private YeopjeonHistoryType type;

    @Column(nullable = false)
    private Long amount;

    @Column(name = "balance_snapshot", nullable = false)
    private Long balanceSnapshot; // 잔액 스냅샷

    @Column
    private String description;

    @Builder
    private YeopjeonHistory(Long userId, Long paymentOrderId, Long shopId, YeopjeonHistoryType type,
            Long amount, Long balanceSnapshot, String description) {
        this.userId = userId;
        this.paymentOrderId = paymentOrderId;
        this.shopId = shopId;
        this.type = type;
        this.amount = amount;
        this.balanceSnapshot = balanceSnapshot;
        this.description = description;
    }

    public static YeopjeonHistory create(Long userId, Long paymentOrderId, Long shopId,
            YeopjeonHistoryType type, Long amount, Long balanceSnapshot, String description) {
        return YeopjeonHistory.builder()
                .userId(userId)
                .paymentOrderId(paymentOrderId)
                .shopId(shopId)
                .type(type)
                .amount(amount)
                .balanceSnapshot(balanceSnapshot)
                .description(description)
                .build();
    }
}
