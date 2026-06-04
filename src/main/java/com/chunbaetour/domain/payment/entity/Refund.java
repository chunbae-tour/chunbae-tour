package com.chunbaetour.domain.payment.entity;

import com.chunbaetour.domain.common.entity.BaseEntity;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.payment.type.RefundStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
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

    @Column(length = 500, nullable = false)
    private String reason;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    // PG 취소 실패 후 자동 재시도 횟수 (최대 5회)
    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    // 다음 재시도 가능 시각 (null이면 재시도 대상 아님)
    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Builder
    private Refund(Long paymentOrderId, Long userId, Long amount, String reason) {
        if (amount == null || amount <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
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

    /** 관리자 승인 시 상태 전이. PENDING 또는 REQUIRES_ADMIN 허용 — 자동 재시도 실패 건도 관리자가 수동 처리 가능. */
    public void approve() {
        if (this.status != RefundStatus.PENDING && this.status != RefundStatus.REQUIRES_ADMIN) {
            throw new BusinessException(ErrorCode.REFUND_INVALID_STATUS_TRANSITION);
        }
        this.status = RefundStatus.APPROVED;
    }

    public void fail(String rejectReason, LocalDateTime nextRetryAt) {
        if (this.status != RefundStatus.PENDING) {
            throw new BusinessException(ErrorCode.REFUND_INVALID_STATUS_TRANSITION);
        }
        this.status = RefundStatus.FAILED;
        this.rejectReason = rejectReason;
        this.nextRetryAt = nextRetryAt;
    }

    /**
     * 스케줄러 재시도 실패 시 다음 시도 시각 갱신.
     * 지수 백오프 정책에 따라 nextRetryAt이 계산되어 전달됨.
     */
    public void recordRetryFailure(LocalDateTime nextRetryAt) {
        this.retryCount++;
        this.nextRetryAt = nextRetryAt;
    }

    /** 최대 재시도 횟수 초과 — 자동 처리 불가, 관리자 직접 확인 필요 상태로 전환. */
    public void requireAdmin() {
        if (this.status != RefundStatus.FAILED) {
            throw new BusinessException(ErrorCode.REFUND_INVALID_STATUS_TRANSITION);
        }
        this.status = RefundStatus.REQUIRES_ADMIN;
        this.nextRetryAt = null;
    }

    /** 스케줄러가 PENDING 또는 FAILED 상태 환불을 APPROVED로 전환. */
    public void approveFromScheduler() {
        if (this.status != RefundStatus.PENDING && this.status != RefundStatus.FAILED) {
            throw new BusinessException(ErrorCode.REFUND_INVALID_STATUS_TRANSITION);
        }
        this.status = RefundStatus.APPROVED;
    }

    /**
     * PG 취소 완료 후 스케줄러가 CANCELLED 상태 환불을 APPROVED로 전환.
     * PG 호출과 사용자 cancelRefund() 사이 경합으로 CANCELLED가 된 경우 사용.
     * PG에서 이미 돈이 반환됐으므로 엽전 회수 후 APPROVED 확정.
     */
    public void approveFromCancelledByScheduler() {
        if (this.status != RefundStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.REFUND_INVALID_STATUS_TRANSITION);
        }
        this.status = RefundStatus.APPROVED;
    }

    /** 관리자 거절 시 상태 전이 + 거절 사유 저장. PENDING 또는 REQUIRES_ADMIN 허용. */
    public void reject(String rejectReason) {
        if (this.status != RefundStatus.PENDING && this.status != RefundStatus.REQUIRES_ADMIN) {
            throw new BusinessException(ErrorCode.REFUND_INVALID_STATUS_TRANSITION);
        }
        this.status = RefundStatus.REJECTED;
        this.rejectReason = rejectReason;
    }

    /** 사용자 취소 시 상태 전이. PENDING이 아니면 PAY_019. */
    public void cancel() {
        if (this.status != RefundStatus.PENDING) {
            throw new BusinessException(ErrorCode.REFUND_CANCEL_NOT_ALLOWED);
        }
        this.status = RefundStatus.CANCELLED;
    }
}
