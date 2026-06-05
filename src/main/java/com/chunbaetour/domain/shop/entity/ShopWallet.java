package com.chunbaetour.domain.shop.entity;

import com.chunbaetour.domain.common.converter.AccountNumberEncryptConverter;
import com.chunbaetour.domain.common.entity.BaseEntity;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
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

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(nullable = false)
    private long balance;

    @Column(name = "bank_name", length = 50)
    private String bankName;

    @Convert(converter = AccountNumberEncryptConverter.class)
    @Column(name = "account_number", length = 255)
    private String accountNumber;

    @Column(name = "account_holder", length = 50)
    private String accountHolder;

    public static ShopWallet create(Long shopId) {
        ShopWallet w = new ShopWallet();
        w.shopId = shopId;
        w.balance = 0L;
        return w;
    }

    /** 정산 계좌 등록/변경 — 기존 계좌 전체 교체 (PUT 의미). null/blank는 DTO 레이어에서 차단되지만 배치·마이그레이션 경로 방어 */
    public void updateAccount(String bankName, String accountNumber, String accountHolder) {
        if (bankName == null || bankName.isBlank()
                || accountNumber == null || accountNumber.isBlank()
                || accountHolder == null || accountHolder.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        this.bankName = bankName.trim();
        this.accountNumber = accountNumber.trim();
        this.accountHolder = accountHolder.trim();
    }

    /** QR 결제 승인 시 상인 잔액 증가 */
    public void credit(long amount) {
        if (amount <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        // 정상 운영에서 발생 불가한 금액 — 데이터 이상 또는 비정상 입력
        if (Long.MAX_VALUE - this.balance < amount) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        this.balance += amount;
    }

    /** 정산 요청 시 잔액 차감 */
    public void debit(long amount) {
        if (amount <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (this.balance < amount) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
        }
        this.balance -= amount;
    }
}
