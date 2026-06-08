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

    record PortOnePaymentInfo(String status, Long totalAmount, Long cancelledAmount) {
        public boolean isPaid() {
            return "PAID".equals(status);
        }

        public boolean isCancelled() {
            return "CANCELLED".equals(status);
        }
    }
}
