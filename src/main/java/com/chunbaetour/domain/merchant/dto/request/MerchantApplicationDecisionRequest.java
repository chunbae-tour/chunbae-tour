package com.chunbaetour.domain.merchant.dto.request;

/** 상인 신청 거절 시 거절 사유 (선택 입력) */
public record MerchantApplicationDecisionRequest(String rejectReason) {
}
