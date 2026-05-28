package com.chunbaetour.domain.shop.entity;

import com.chunbaetour.domain.common.entity.BaseEntity;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.shop.type.SettlementStatus;
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
import org.springframework.util.StringUtils;

@Entity
@Table(name = "settlements", indexes = {
        @Index(name = "idx_settlements_shop_id", columnList = "shop_id"),
        @Index(name = "idx_settlements_shop_id_status", columnList = "shop_id, status")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Settlement extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    /** 신청 시점 ShopWallet 잔액 스냅샷 */
    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SettlementStatus status;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    /** 신청 시점 계좌 스냅샷 */
    @Column(name = "bank_name", nullable = false, length = 50)
    private String bankName;

    @Column(name = "account_number", nullable = false, length = 30)
    private String accountNumber;

    @Column(name = "account_holder", nullable = false, length = 50)
    private String accountHolder;

    public static Settlement create(Long shopId, long amount,
            String bankName, String accountNumber, String accountHolder) {
        Settlement s = new Settlement();
        s.shopId = shopId;
        s.amount = amount;
        s.status = SettlementStatus.PENDING;
        s.bankName = bankName;
        s.accountNumber = accountNumber;
        s.accountHolder = accountHolder;
        return s;
    }

    public void approve() {
        if (this.status != SettlementStatus.PENDING) {
            throw new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS);
        }
        this.status = SettlementStatus.APPROVED;
    }

    public void reject(String reason) {
        if (this.status != SettlementStatus.PENDING) {
            throw new BusinessException(ErrorCode.SETTLEMENT_INVALID_STATUS);
        }
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        this.status = SettlementStatus.REJECTED;
        this.rejectReason = reason.trim();
    }
}
