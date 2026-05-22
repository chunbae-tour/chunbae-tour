package com.chunbaetour.domain.merchant.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.merchant.dto.request.MerchantApplyRequest;
import com.chunbaetour.domain.merchant.dto.response.MerchantApplicationResponse;
import com.chunbaetour.domain.merchant.entity.MerchantApplication;
import com.chunbaetour.domain.merchant.repository.MerchantApplicationRepository;
import com.chunbaetour.domain.merchant.type.MerchantApplicationStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상인 신청 서비스 (STORY-08).
 * 신청 → PENDING 생성. 관리자 승인/거절은 STORY-09(AdminMerchantApplicationService).
 *
 * TODO [STORY-09]: AdminMerchantApplicationService 구현 필요.
 *   - PENDING 목록 조회 (커서 기반 페이지네이션)
 *   - 승인: application.approve() → user.role USER→MERCHANT → Shop 생성 (단일 트랜잭션)
 *   - 거절: application.reject(reason)
 *   - 동시 승인/거절 race condition → findByIdWithLock (비관적 락)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MerchantApplicationService {

    private final MerchantApplicationRepository merchantApplicationRepository;

    /**
     * 상인 등록 신청.
     * 중복 신청(PENDING) 방지 → 사업자번호 유효성 검증 → 신청 저장.
     */
    @Transactional
    public MerchantApplicationResponse apply(Long userId, MerchantApplyRequest request) {
        // PENDING 또는 APPROVED 신청이 이미 있으면 중복 신청 불가 (MERCHANT_001)
        if (merchantApplicationRepository.existsByUserIdAndStatusIn(userId,
                List.of(MerchantApplicationStatus.PENDING, MerchantApplicationStatus.APPROVED))) {
            throw new BusinessException(ErrorCode.MERCHANT_CERT_ALREADY_PENDING);
        }

        // 사업자등록번호 체크섬 유효성 검증 (MERCHANT_002)
        String normalized = request.businessNumber().replace("-", "");
        if (!isValidBusinessNumber(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_BUSINESS_NUMBER);
        }

        try {
            MerchantApplication application = merchantApplicationRepository.saveAndFlush(
                    MerchantApplication.create(userId, request)
            );
            return MerchantApplicationResponse.from(application);
        } catch (DataIntegrityViolationException e) {
            // check-then-act 사이 동시 요청이 먼저 커밋된 경우
            throw new BusinessException(ErrorCode.MERCHANT_CERT_ALREADY_PENDING);
        }
    }

    /**
     * 한국 사업자등록번호 체크섬 검증 (국세청 NTS 알고리즘).
     * d[0..7]은 각 가중치 곱을 합산, d[8]은 d[8]*5의 십의 자리(캐리)만 가산.
     */
    private boolean isValidBusinessNumber(String digits) {
        if (digits.length() != 10) return false;
        int[] weights = {1, 3, 7, 1, 3, 7, 1, 3, 5};
        int sum = 0;
        for (int i = 0; i < 8; i++) {
            sum += (digits.charAt(i) - '0') * weights[i];
        }
        sum += ((digits.charAt(8) - '0') * 5) / 10;
        int checkDigit = (10 - (sum % 10)) % 10;
        return checkDigit == (digits.charAt(9) - '0');
    }
}
