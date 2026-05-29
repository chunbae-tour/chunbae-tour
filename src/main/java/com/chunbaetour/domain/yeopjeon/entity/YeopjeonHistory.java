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
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 엽전(포인트) 입출금 이력 엔티티.
 * type 필드로 CHARGE(충전) / PAYMENT(결제) / RECEIVED_PAYMENT(받은 결제) / REFUND(환불) 구분.
 * balanceSnapshot: 이 이력 발생 직후 잔액 — 이력만으로 잔액 추적 가능하게 보존.
 * cursor 페이징을 위해 (user_id, id) 복합 인덱스 사용.
 */
@Entity
@Table(name = "yeopjeon_histories", indexes = @Index(name = "idx_yeopjeon_history_cursor", columnList = "user_id, id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class YeopjeonHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "payment_order_id")
    private Long paymentOrderId;

    // shopId: PAYMENT/RECEIVED_PAYMENT 타입에서 거래 상점 식별. CHARGE/REFUND에선 null.
    @Column(name = "shop_id")
    private Long shopId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private YeopjeonHistoryType type;

    @Column(nullable = false)
    private Long amount;

    @Column(name = "balance_snapshot", nullable = false)
    private Long balanceSnapshot; // 잔액 스냅샷

    // 거래 메모 (예: "우리떡볶이로 1000엽전 발행"). 입력 선택, 최대 100자.
    @Column(length = 100)
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

    // 엽전 충전 이력 전용 팩토리. shopId·description은 충전 시 해당 없으므로 null 고정
    public static YeopjeonHistory ofCharge(Long userId, Long amount, Long balanceSnapshot, Long paymentOrderId) {
        return YeopjeonHistory.builder()
                .userId(userId)
                .paymentOrderId(paymentOrderId)
                .shopId(null)
                .type(YeopjeonHistoryType.CHARGE)
                .amount(amount)
                .balanceSnapshot(balanceSnapshot)
                .description(null)
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

    /** 스토어 상품 구매 차감 이력. paymentOrderId/shopId는 스토어 구매와 직접 연결되지 않아 비워둔다. */
    public static YeopjeonHistory ofStorePurchase(
            Long userId, Long amount, Long balanceSnapshot, String productName) {
        return YeopjeonHistory.builder()
                .userId(userId)
                .type(YeopjeonHistoryType.PAYMENT)
                .amount(amount)
                .balanceSnapshot(balanceSnapshot)
                .description("스토어 구매 - " + productName)
                .build();
    }
}
