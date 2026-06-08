package com.chunbaetour.domain.payment.dto.request;

/** 환불 거절 요청 DTO. reason은 선택 입력 — null이면 사유 미기재로 처리. */
public record RefundRejectRequest(String reason) {
}
