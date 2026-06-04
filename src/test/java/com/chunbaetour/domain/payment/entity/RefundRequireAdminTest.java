package com.chunbaetour.domain.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.payment.type.RefundStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Refund.requireAdmin() 도메인 단위 테스트.
 * PENDING 진입 허용 버그픽스 검증 — order==null 케이스 무한루프 방지.
 */
class RefundRequireAdminTest {

    private Refund createRefund(RefundStatus status) {
        Refund refund = Refund.create(1L, 1L, 10000L, "환불 요청");
        ReflectionTestUtils.setField(refund, "status", status);
        return refund;
    }

    @Test
    @DisplayName("PENDING 상태에서 requireAdmin() 호출 — REQUIRES_ADMIN 전환 성공")
    void requireAdmin_fromPending_success() {
        Refund refund = createRefund(RefundStatus.PENDING);

        refund.requireAdmin();

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUIRES_ADMIN);
        assertThat(refund.getNextRetryAt()).isNull();
    }

    @Test
    @DisplayName("FAILED 상태에서 requireAdmin() 호출 — REQUIRES_ADMIN 전환 성공")
    void requireAdmin_fromFailed_success() {
        Refund refund = createRefund(RefundStatus.FAILED);

        refund.requireAdmin();

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUIRES_ADMIN);
        assertThat(refund.getNextRetryAt()).isNull();
    }

    @Test
    @DisplayName("APPROVED 상태에서 requireAdmin() 호출 — REFUND_INVALID_STATUS_TRANSITION")
    void requireAdmin_fromApproved_throws() {
        Refund refund = createRefund(RefundStatus.APPROVED);

        assertThatThrownBy(refund::requireAdmin)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.REFUND_INVALID_STATUS_TRANSITION);
    }

    @Test
    @DisplayName("REQUIRES_ADMIN 상태에서 requireAdmin() 재호출 — REFUND_INVALID_STATUS_TRANSITION")
    void requireAdmin_fromRequiresAdmin_throws() {
        Refund refund = createRefund(RefundStatus.REQUIRES_ADMIN);

        assertThatThrownBy(refund::requireAdmin)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.REFUND_INVALID_STATUS_TRANSITION);
    }
}
