package com.chunbaetour.domain.yeopjeon.entity;

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

@Entity
@Table(name = "wallets", uniqueConstraints = @UniqueConstraint(name = "uk_wallets_user_id", columnNames = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Wallet extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private long balance;

    public static Wallet create(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        Wallet wallet = new Wallet();
        wallet.userId = userId;
        wallet.balance = 0L;
        return wallet;
    }

    public void credit(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (Long.MAX_VALUE - this.balance < amount) {
            throw new IllegalArgumentException("balance overflow");
        }
        this.balance += amount;
    }

    /** 엽전 차감. 잔액 부족 시 IllegalArgumentException — WalletService가 사전 확인하지만 엔티티도 자체 방어. */
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
