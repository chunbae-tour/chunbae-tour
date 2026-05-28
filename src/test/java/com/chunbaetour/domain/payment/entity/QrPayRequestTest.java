package com.chunbaetour.domain.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.payment.type.QrPayStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QrPayRequestTest {

    private static final LocalDateTime EXPIRED_AT = LocalDateTime.of(2026, 5, 25, 10, 5);

    @Test
    @DisplayName("QR 결제 완료 — 완료 시각이 null이면 예외")
    void complete_with_null_completedAt_throws() {
        QrPayRequest request = createPendingRequest();

        assertThatThrownBy(() -> request.complete(null))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.QR_PAY_INVALID_STATUS_TRANSITION);

        assertThat(request.getStatus()).isEqualTo(QrPayStatus.PENDING);
        assertThat(request.getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("QR 결제 완료 — 완료 시각을 저장하고 완료 상태로 전이")
    void complete_success() {
        QrPayRequest request = createPendingRequest();
        LocalDateTime completedAt = LocalDateTime.of(2026, 5, 25, 10, 1);

        request.complete(completedAt);

        assertThat(request.getStatus()).isEqualTo(QrPayStatus.COMPLETED);
        assertThat(request.getCompletedAt()).isEqualTo(completedAt);
        assertThat(request.getPendingKey()).isNull();
    }

    private QrPayRequest createPendingRequest() {
        return QrPayRequest.create("pay-request-id", 1L, 10L, 5_000L, "[]", EXPIRED_AT);
    }
}
