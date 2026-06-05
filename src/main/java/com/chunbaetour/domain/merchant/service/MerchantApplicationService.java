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
import org.hibernate.exception.ConstraintViolationException;
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

    // 동일 사용자의 중복 신청 방지: PENDING인 건이 있으면 차단 (APPROVED는 허용 — 1상인 다중 가게)
    private static final List<MerchantApplicationStatus> USER_PENDING_STATUSES =
            List.of(MerchantApplicationStatus.PENDING);
    // 사업자번호 중복 방지: PENDING·APPROVED 모두 포함 (다른 사용자의 활성 신청 차단)
    private static final List<MerchantApplicationStatus> BIZ_ACTIVE_STATUSES =
            List.of(MerchantApplicationStatus.PENDING, MerchantApplicationStatus.APPROVED);

    private final MerchantApplicationRepository merchantApplicationRepository;

    /**
     * 상인 등록 신청.
     * 중복 신청(PENDING) 방지 → 사업자번호 유효성 검증 → 사업자번호 중복 검사 → 신청 저장.
     * APPROVED 상태는 차단하지 않음 — 1상인 다중 가게 지원.
     *
     * <p>사업자번호 동시성: existsByBusinessNumberAndStatusIn + save 사이 check-then-act race condition을
     * uk_merchant_active_business_number (business_number, active_flag) 유니크 제약으로 최종 차단.
     * MySQL unique index에서 NULL은 서로 다른 값으로 취급 → active_flag=null(REJECTED)은 제약 대상 제외,
     * active_flag='1'(PENDING/APPROVED)만 중복 INSERT 시 DataIntegrityViolationException 발생.
     *
     * <p>유저 동시성: existsByUserIdAndStatusIn + save 사이 race condition을
     * uk_merchant_active_user_id (user_id, active_flag) 유니크 제약으로 최종 차단.
     * APPROVED 시 activeFlag=null로 초기화 → MySQL NULL 취급으로 제약 해제 → 동일 상인의 추가 가게 신청 허용.
     */
    @Transactional
    public MerchantApplicationResponse apply(Long userId, MerchantApplyRequest request) {
        // 이미 PENDING 신청이 있으면 중복 신청 불가 (MERCHANT_001)
        if (merchantApplicationRepository.existsByUserIdAndStatusIn(userId, USER_PENDING_STATUSES)) {
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
        if (merchantApplicationRepository.existsByBusinessNumberAndStatusIn(normalized, BIZ_ACTIVE_STATUSES)) {
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

    /**
     * DataIntegrityViolationException 예외 체인에서 특정 제약 이름이 포함된 메시지가 있는지 확인.
     * MySQL은 중첩 예외 체인으로 제약 이름을 전달하므로 getCause()를 타고 전체 체인을 탐색한다.
     */
    private boolean containsConstraint(DataIntegrityViolationException e, String constraintName) {
        Throwable cause = e;
        while (cause != null) {
            // Hibernate ConstraintViolationException에서 제약 이름을 직접 추출 (드라이버 버전 독립적)
            if (cause instanceof ConstraintViolationException cve
                    && constraintName.equals(cve.getConstraintName())) {
                return true;
            }
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
        // DTO 검증 선행을 신뢰하지만, 비숫자 문자 혼입 시 charAt - '0' 계산이 잘못되므로 방어 체크
        if (!digits.chars().allMatch(Character::isDigit)) return false;
        int[] weights = {1, 3, 7, 1, 3, 7, 1, 3, 5};
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += (digits.charAt(i) - '0') * weights[i];
        }
        // d[8]*5의 십의 자리(캐리)만 추가 합산
        sum += ((digits.charAt(8) - '0') * 5) / 10;
        int checkDigit = (10 - (sum % 10)) % 10;
        return checkDigit == (digits.charAt(9) - '0');
    }
}
