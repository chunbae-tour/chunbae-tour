package com.chunbaetour.domain.payment.type;

public enum RefundStatus {
    PENDING,        // 환불 요청 접수 — 스케줄러가 PG 취소 처리 대기 중
    APPROVED,       // 환불 완료 — PG 취소 성공, 엽전 회수 완료
    REJECTED,       // 환불 불가 — 부분 사용 등 정책상 거절 (이력 보존용)
    CANCELLED,      // 사용자 철회 — PG 호출 전 본인이 요청 취소
    FAILED,         // PG 취소 실패 — 재시도 스케줄러 처리 대기 중
    REQUIRES_ADMIN  // 재시도 횟수 초과 — 자동 처리 불가, 관리자 직접 확인 필요
}
