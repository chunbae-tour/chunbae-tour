package com.chunbaetour.domain.shop.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.shop.type.ShopCertificationStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link ShopCertification} 도메인 메서드 단위 테스트 (KAN-204 Admin S05).
 *
 * <p>승인/거절 상태 가드(PENDING 외 호출 시 409) + 필드 세팅 검증.
 */
class ShopCertificationTest {

    private static final LocalDateTime SUBMITTED_AT = LocalDateTime.of(2026, 6, 1, 10, 0);
    private static final LocalDateTime PROCESSED_AT = LocalDateTime.of(2026, 6, 2, 12, 0);

    private ShopCertification pending() {
        return ShopCertification.submit(100L, "사업자등록증 첨부", SUBMITTED_AT);
    }

    @Nested
    @DisplayName("approve()")
    class Approve {

        @Test
        @DisplayName("PENDING 승인 → APPROVED + processedBy/processedAt 세팅")
        void approve_from_pending() {
            ShopCertification cert = pending();

            cert.approve(7L, PROCESSED_AT);

            assertThat(cert.getStatus()).isEqualTo(ShopCertificationStatus.APPROVED);
            assertThat(cert.getProcessedBy()).isEqualTo(7L);
            assertThat(cert.getProcessedAt()).isEqualTo(PROCESSED_AT);
            assertThat(cert.getRejectReason()).isNull();
        }

        @Test
        @DisplayName("이미 APPROVED 재승인 → 409 SHOP_CERTIFICATION_INVALID_STATUS")
        void approve_when_not_pending_throws() {
            ShopCertification cert = pending();
            cert.approve(7L, PROCESSED_AT);

            assertThatThrownBy(() -> cert.approve(7L, PROCESSED_AT))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SHOP_CERTIFICATION_INVALID_STATUS);
        }

        @Test
        @DisplayName("이미 REJECTED 승인 → 409")
        void approve_when_rejected_throws() {
            ShopCertification cert = pending();
            cert.reject(7L, "기준 미달", PROCESSED_AT);

            assertThatThrownBy(() -> cert.approve(7L, PROCESSED_AT))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SHOP_CERTIFICATION_INVALID_STATUS);
        }
    }

    @Nested
    @DisplayName("reject()")
    class Reject {

        @Test
        @DisplayName("PENDING 거절 → REJECTED + rejectReason/processedBy/processedAt 세팅")
        void reject_from_pending() {
            ShopCertification cert = pending();

            cert.reject(9L, "위생 기준 미달", PROCESSED_AT);

            assertThat(cert.getStatus()).isEqualTo(ShopCertificationStatus.REJECTED);
            assertThat(cert.getRejectReason()).isEqualTo("위생 기준 미달");
            assertThat(cert.getProcessedBy()).isEqualTo(9L);
            assertThat(cert.getProcessedAt()).isEqualTo(PROCESSED_AT);
        }

        @Test
        @DisplayName("이미 처리된 신청 거절 → 409")
        void reject_when_not_pending_throws() {
            ShopCertification cert = pending();
            cert.approve(7L, PROCESSED_AT);

            assertThatThrownBy(() -> cert.reject(9L, "사유", PROCESSED_AT))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SHOP_CERTIFICATION_INVALID_STATUS);
        }
    }

    @Nested
    @DisplayName("cancel()")
    class Cancel {

        private ShopCertification approved() {
            ShopCertification cert = pending();
            cert.approve(7L, PROCESSED_AT);
            return cert;
        }

        @Test
        @DisplayName("APPROVED 취소 → CANCELLED + cancelReason/processedBy/processedAt 세팅")
        void cancel_from_approved() {
            ShopCertification cert = approved();

            cert.cancel(11L, "인증 기준 미충족 발견", PROCESSED_AT);

            assertThat(cert.getStatus()).isEqualTo(ShopCertificationStatus.CANCELLED);
            assertThat(cert.getCancelReason()).isEqualTo("인증 기준 미충족 발견");
            assertThat(cert.getProcessedBy()).isEqualTo(11L);
            assertThat(cert.getProcessedAt()).isEqualTo(PROCESSED_AT);
        }

        @Test
        @DisplayName("PENDING 취소 → 409 SHOP_CERTIFICATION_INVALID_STATUS")
        void cancel_when_pending_throws() {
            ShopCertification cert = pending();

            assertThatThrownBy(() -> cert.cancel(11L, "사유", PROCESSED_AT))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SHOP_CERTIFICATION_INVALID_STATUS);
        }

        @Test
        @DisplayName("REJECTED 취소 → 409")
        void cancel_when_rejected_throws() {
            ShopCertification cert = pending();
            cert.reject(9L, "기준 미달", PROCESSED_AT);

            assertThatThrownBy(() -> cert.cancel(11L, "사유", PROCESSED_AT))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SHOP_CERTIFICATION_INVALID_STATUS);
        }
    }

    @Test
    @DisplayName("submit() → 초기 상태 PENDING")
    void submit_initial_status_pending() {
        ShopCertification cert = pending();

        assertThat(cert.getStatus()).isEqualTo(ShopCertificationStatus.PENDING);
        assertThat(cert.getProcessedBy()).isNull();
        assertThat(cert.getProcessedAt()).isNull();
        assertThat(cert.getShopId()).isEqualTo(100L);
    }
}
