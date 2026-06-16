package com.chunbaetour.domain.payment.client;

/**
 * PG사(결제대행사) 연동 추상화 인터페이스.
 *
 * ChargeService는 이 인터페이스만 바라보며, 실제 PG사가 포트원인지 다른 곳인지 모른다.
 * 실 배포: PortOnePaymentGatewayClient가 주입됨.
 * 테스트: Mockito로 이 인터페이스를 모킹해 실제 PG 호출 없이 테스트 가능.
 */
public interface PaymentGatewayClient {

    // 결제 사전등록 — 프론트 SDK 결제 전 포트원에 금액을 미리 등록해 위변조 방지
    void preRegister(String orderUid, Long amount);

    // 결제 검증 — 콜백 수신 후 포트원에서 실제 결제 상태/금액 확인 (위변조 방지)
    PortOnePaymentInfo verifyPayment(String paymentId);

    // 결제 취소(환불) — 관리자 환불 승인 시 포트원에 전액 취소 요청.
    // idempotencyKey: "refund-{refundId}" 형식으로 전달해 네트워크 타임아웃 후 재시도 시 이중 취소 방지.
    void cancelPayment(String orderUid, Long amount, String reason, String idempotencyKey);

    record PortOnePaymentInfo(String status, Long totalAmount, Long cancelledAmount, String transactionId) {
        /**
         * transactionId가 불필요한 경로(취소·부분취소 검증, 다수 테스트) 호환용 3-arg 생성자.
         * 고아 PENDING 재조정(B3)에서 PAID 복구 시 CallbackService.handleSuccess(orderUid, txId)에
         * 넘길 PG 거래번호가 필요해 canonical에 transactionId를 추가했으나, 기존 호출은 무변경 유지한다.
         */
        public PortOnePaymentInfo(String status, Long totalAmount, Long cancelledAmount) {
            this(status, totalAmount, cancelledAmount, null);
        }

        public boolean isPaid() {
            return "PAID".equals(status);
        }

        public boolean isCancelled() {
            return "CANCELLED".equals(status);
        }

        public boolean isFailed() {
            return "FAILED".equals(status);
        }
    }
}
