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
 * 신청 → PENDING 생성. 관리자 승인/거절은 AdminMerchantApplicationService(STORY-09).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MerchantApplicationService {

    private static final List<MerchantApplicationStatus> ACTIVE_STATUSES =
            List.of(MerchantApplicationStatus.PENDING, MerchantApplicationStatus.APPROVED);

    private final MerchantApplicationRepository merchantApplicationRepository;

    /**
     * 상인 등록 신청.
     * 중복 신청(PENDING/APPROVED) 방지 → 사업자번호 유효성 검증 → 사업자번호 중복 검사 → 신청 저장.
     *
     * <p>사업자번호 동시성: existsByBusinessNumberAndStatusIn + save 사이 check-then-act race condition을
     * uk_merchant_active_business_number (business_number, active_flag) 유니크 제약으로 최종 차단.
     * MySQL unique index에서 NULL은 서로 다른 값으로 취급 → active_flag=null(REJECTED)은 제약 대상 제외,
     * active_flag='1'(PENDING/APPROVED)만 중복 INSERT 시 DataIntegrityViolationException 발생.
     *
     * <p><b>Known limitation</b>: existsByUserIdAndStatusIn(userId) 체크는 DB 레벨 보호 없음.
     * 동일 유저의 동시 신청은 실사용에서 극히 드문 케이스이므로 허용된 한계로 남긴다.
     */
    @Transactional
    public MerchantApplicationResponse apply(Long userId, MerchantApplyRequest request) {
        // PENDING 또는 APPROVED 신청이 이미 있으면 중복 신청 불가 (MERCHANT_001)
        if (merchantApplicationRepository.existsByUserIdAndStatusIn(userId, ACTIVE_STATUSES)) {
            throw new BusinessException(ErrorCode.MERCHANT_CERT_ALREADY_PENDING);
        }

        // 사업자등록번호 체크섬 유효성 검증 (MERCHANT_002)
        // DTO에서 ^\d{10}$|^\d{3}-\d{2}-\d{5}$ 패턴 검증이 선행되므로 하이픈 제거 후 항상 10자리 숫자.
        // isValidBusinessNumber는 DTO 검증을 신뢰하지만, 독립적으로 호출되는 경우를 대비해 길이 체크를 내부에서 수행한다.
        String normalized = request.businessNumber().replace("-", "");
        if (!isValidBusinessNumber(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_BUSINESS_NUMBER);
        }

        // 다른 유저가 동일 사업자번호로 이미 PENDING/APPROVED 신청 중이면 차단 (MERCHANT_004)
        // REJECTED된 신청의 번호는 재사용 허용 — 코드 레벨 선제 차단 + DB 제약 최종 방어선
        if (merchantApplicationRepository.existsByBusinessNumberAndStatusIn(normalized, ACTIVE_STATUSES)) {
            throw new BusinessException(ErrorCode.DUPLICATE_BUSINESS_NUMBER);
        }

        try {
            MerchantApplication application = merchantApplicationRepository.saveAndFlush(
                    MerchantApplication.create(userId, request)
            );
            return MerchantApplicationResponse.from(application);
        } catch (DataIntegrityViolationException e) {
            if (containsConstraint(e, "uk_merchant_active_user_id")) {
                throw new BusinessException(ErrorCode.MERCHANT_CERT_ALREADY_PENDING);
            }
            if (containsConstraint(e, "uk_merchant_active_business_number")) {
                throw new BusinessException(ErrorCode.DUPLICATE_BUSINESS_NUMBER);
            }
            throw e;
        }
    }

    private boolean containsConstraint(DataIntegrityViolationException e, String constraintName) {
        Throwable cause = e;
        while (cause != null) {
            String message = cause.getMessage();
            if (message != null && message.contains(constraintName)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    /**
     * 한국 사업자등록번호 체크섬 검증 (국세청 NTS 알고리즘).
     * d[0..7]은 각 가중치 곱을 합산, d[8]은 d[8]*5의 십의 자리(캐리)만 가산.
     * 호출 전 digits가 10자리 숫자임을 보장해야 한다 (DTO 패턴 검증 + replace("-","") 선행).
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
