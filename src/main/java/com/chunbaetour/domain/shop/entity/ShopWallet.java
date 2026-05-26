package com.chunbaetour.domain.shop.entity;

import com.chunbaetour.domain.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상인 엽전 지갑 엔티티.
 * QR 결제 승인 시 상인 잔액 증가, 정산 요청 시 차감.
 * 가게 승인(MerchantApplicationService) 시 함께 생성.
 */
@Entity
@Table(name = "shop_wallets",
        uniqueConstraints = @UniqueConstraint(name = "uk_shop_wallets_shop_id", columnNames = "shop_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShopWallet extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(nullable = false)
    private long balance;

    @Column(name = "bank_name", length = 50)
    private String bankName;

    @Column(name = "account_number", length = 30)
    private String accountNumber;

    @Column(name = "account_holder", length = 50)
    private String accountHolder;

    public static ShopWallet create(Long shopId) {
        ShopWallet w = new ShopWallet();
        w.shopId = shopId;
        w.balance = 0L;
        return w;
    }

    /** QR 결제 승인 시 상인 잔액 증가 */
    public void credit(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (Long.MAX_VALUE - this.balance < amount) {
            throw new IllegalArgumentException("balance overflow");
        }
        this.balance += amount;
    }

    /** 정산 요청 시 잔액 차감 */
    public void debit(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (this.balance < amount) {
            throw new IllegalArgumentException("insufficient balance");
        }
        this.balance -= amount;
    }
}
