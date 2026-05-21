package com.chunbaetour.domain.merchant.service;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.merchant.dto.request.MerchantApplyRequest;
import com.chunbaetour.domain.merchant.dto.response.MerchantApplicationResponse;
import com.chunbaetour.domain.merchant.entity.MerchantApplication;
import com.chunbaetour.domain.merchant.repository.MerchantApplicationRepository;
import com.chunbaetour.domain.merchant.type.MerchantApplicationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상인 신청 서비스 (STORY-08).
 * 신청 → PENDING 생성. 관리자 승인/거절은 STORY-09(AdminMerchantApplicationService).
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
        // PENDING 상태 신청이 이미 있으면 중복 신청 불가 (MERCHANT_001)
        if (merchantApplicationRepository.existsByUserIdAndStatus(userId, MerchantApplicationStatus.PENDING)) {
            throw new BusinessException(ErrorCode.MERCHANT_CERT_ALREADY_PENDING);
        }

        // 사업자등록번호 체크섬 유효성 검증 (MERCHANT_002)
        String normalized = request.businessNumber().replace("-", "");
        if (!isValidBusinessNumber(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_BUSINESS_NUMBER);
        }

        MerchantApplication application = merchantApplicationRepository.save(
                MerchantApplication.create(userId, request)
        );

        return MerchantApplicationResponse.from(application);
    }

    /**
     * 한국 사업자등록번호 체크섬 검증.
     * weights = [1, 3, 7, 1, 3, 7, 1, 3, 5], 마지막 자리가 check digit.
     */
    private boolean isValidBusinessNumber(String digits) {
        if (digits.length() != 10) return false;
        int[] weights = {1, 3, 7, 1, 3, 7, 1, 3, 5};
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += (digits.charAt(i) - '0') * weights[i];
        }
        // 9번째 자리 * 5의 십의 자리 가산
        sum += ((digits.charAt(8) - '0') * 5) / 10;
        int checkDigit = (10 - (sum % 10)) % 10;
        return checkDigit == (digits.charAt(9) - '0');
    }
}
