package com.chunbaetour.domain.store.entity;

import com.chunbaetour.domain.common.entity.BaseEntity;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.store.type.UserItemStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_items", indexes = @Index(name = "idx_user_item_user_id_id", columnList = "user_id, id DESC"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long productId;

    /** 구매 시점 상품명 스냅샷 */
    @Column(nullable = false, length = 100)
    private String productName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserItemStatus status;

    /** 사용 만료일 — product.validityDays 기반 계산. null = 무기한 */
    @Column
    private LocalDate expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "used_shop_id")
    private Long usedShopId;

    @Builder
    private UserItem(Long userId, Long orderId, Long productId, String productName,
                     UserItemStatus status, LocalDate expiresAt) {
        this.userId = userId;
        this.orderId = orderId;
        this.productId = productId;
        this.productName = productName;
        this.status = status;
        this.expiresAt = expiresAt;
    }

    /** today — 서비스에서 Clock 기반으로 계산해 전달 (테스트 시 고정 날짜 주입 가능) */
    public static UserItem create(Long userId, Long orderId, Product product, LocalDate today) {
        return UserItem.builder()
                .userId(userId)
                .orderId(orderId)
                .productId(product.getId())
                .productName(product.getName())
                .status(UserItemStatus.AVAILABLE)
                .expiresAt(product.getValidityDays() != null
                        ? today.plusDays(product.getValidityDays())
                        : null)
                .build();
    }

    public void use(LocalDateTime usedAt, Long usedShopId) {
        // AVAILABLE만 사용 가능 — 엔티티 불변식을 서비스 검증에 기대지 않고 자체적으로 명시한다.
        // (향후 enum 확장/데이터 오염 대비, 서비스의 status != AVAILABLE → INVALID_REQUEST 정책과 정합)
        if (this.status != UserItemStatus.AVAILABLE) {
            if (this.status == UserItemStatus.USED) {
                throw new BusinessException(ErrorCode.ITEM_ALREADY_USED);
            }
            if (this.status == UserItemStatus.EXPIRED) {
                throw new BusinessException(ErrorCode.ITEM_EXPIRED);
            }
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        this.status = UserItemStatus.USED;
        this.usedAt = usedAt;
        this.usedShopId = usedShopId;
    }
}
