package com.chunbaetour.domain.payment.entity;

import com.chunbaetour.domain.common.entity.BaseEntity;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.payment.type.QrPayStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * QR 결제 요청 엔티티 (STORY-13).
 * 사용자가 QR 스캔 후 결제 요청을 생성하면 PENDING 상태로 저장.
 * 상인이 승인/거절하거나 5분 타임아웃 시 상태 전이.
 * menu_items: 결제 시점 메뉴 정보 JSON 스냅샷 — 이후 메뉴 수정/삭제돼도 영수증 보존.
 */
@Entity
@Table(name = "qr_pay_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QrPayRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 클라이언트에 노출되는 외부 식별자 (UUID) — PK id는 내부용
    @Column(name = "pay_request_id", nullable = false, unique = true, length = 36)
    private String payRequestId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(nullable = false)
    private Long amount;

    // 결제 시점 메뉴 정보 JSON 스냅샷 — [{menuId, name, price, quantity}]
    // 이후 메뉴 수정/삭제돼도 영수증에 당시 가격·이름 보존
    @Column(name = "menu_items", columnDefinition = "JSON", nullable = false)
    private String menuItems;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QrPayStatus status;

    // 상인이 결제 거절 시 남기는 사유 (STORY-14) — PENDING/COMPLETED/EXPIRED 상태에서는 null
    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

    // 요청 생성 시각 + 5분 — 상인이 이 시각까지 미응답 시 STORY-15 스케줄러가 EXPIRED 처리
    @Column(name = "expired_at", nullable = false)
    private LocalDateTime expiredAt;

    // 상인이 승인해 COMPLETED로 전이된 시각 — updatedAt은 이후 수정에도 바뀌므로 완료 시각 전용 컬럼으로 보존
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // PENDING 상태일 때만 "{userId}_{shopId}" 값을 가짐 — DB unique 제약으로 동시 PENDING 중복 방지
    // 상태 전이(complete/reject/expire) 시 null 로 초기화 → null 은 unique 제약 대상 외
    @Column(name = "pending_key", unique = true)
    private String pendingKey;

    @Builder
    private QrPayRequest(String payRequestId, Long userId, Long shopId, Long amount,
            String menuItems, LocalDateTime expiredAt) {
        this.payRequestId = payRequestId;
        this.userId = userId;
        this.shopId = shopId;
        this.amount = amount;
        this.menuItems = menuItems;
        this.status = QrPayStatus.PENDING;
        this.expiredAt = expiredAt;
        this.pendingKey = userId + "_" + shopId;
    }

    public static QrPayRequest create(String payRequestId, Long userId, Long shopId, Long amount,
            String menuItems, LocalDateTime expiredAt) {
        return QrPayRequest.builder()
                .payRequestId(payRequestId)
                .userId(userId)
                .shopId(shopId)
                .amount(amount)
                .menuItems(menuItems)
                .expiredAt(expiredAt)
                .build();
    }

    /** 상인 승인 — STORY-14에서 호출 */
    public void complete(LocalDateTime completedAt) {
        if (this.status != QrPayStatus.PENDING) {
            throw new BusinessException(ErrorCode.QR_PAY_INVALID_STATUS_TRANSITION);
        }
        this.completedAt = Objects.requireNonNull(completedAt, "completedAt must not be null");
        this.status = QrPayStatus.COMPLETED;
        this.pendingKey = null;
    }

    /** 테스트/기존 호출 호환용 — 운영 서비스에서는 Clock 기반 complete(LocalDateTime)을 사용한다. */
    public void complete() {
        complete(LocalDateTime.now());
    }

    /** 상인 거절 — STORY-14에서 호출 */
    public void reject(String reason) {
        if (this.status != QrPayStatus.PENDING) {
            throw new BusinessException(ErrorCode.QR_PAY_INVALID_STATUS_TRANSITION);
        }
        this.status = QrPayStatus.REJECTED;
        this.rejectReason = reason;
        this.pendingKey = null;
    }

    /** 5분 타임아웃 만료 — STORY-15 스케줄러에서 호출 */
    public void expire() {
        if (this.status != QrPayStatus.PENDING) {
            throw new BusinessException(ErrorCode.QR_PAY_INVALID_STATUS_TRANSITION);
        }
        this.status = QrPayStatus.EXPIRED;
        this.pendingKey = null;
    }
}
