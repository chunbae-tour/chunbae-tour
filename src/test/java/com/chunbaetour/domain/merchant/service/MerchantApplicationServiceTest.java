package com.chunbaetour.domain.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.merchant.dto.request.MerchantApplyRequest;
import com.chunbaetour.domain.merchant.dto.response.MerchantApplicationResponse;
import com.chunbaetour.domain.merchant.entity.MerchantApplication;
import com.chunbaetour.domain.merchant.repository.MerchantApplicationRepository;
import com.chunbaetour.domain.merchant.type.MerchantApplicationStatus;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MerchantApplicationServiceTest {

    @Mock
    private MerchantApplicationRepository merchantApplicationRepository;

    @InjectMocks
    private MerchantApplicationService merchantApplicationService;

    private static final Long USER_ID = 1L;

    // NTS 정식 알고리즘 기준 유효한 번호: 101-81-34618 (체크섬=8)
    private static final String VALID_BIZ_NUMBER = "101-81-34618";
    private static final String VALID_BIZ_NORMALIZED = "1018134618";

    private static final List<MerchantApplicationStatus> BLOCKING_STATUSES =
            List.of(MerchantApplicationStatus.PENDING, MerchantApplicationStatus.APPROVED);

    private MerchantApplyRequest makeRequest(String shopName, String bizNumber) {
        return new MerchantApplyRequest(
                shopName,
                bizNumber,
                "한식",
                "서울시 종로구",
                new BigDecimal("37.5700000"),
                new BigDecimal("126.9790000"),
                "02-1234-5678",
                "맛있는 가게"
        );
    }

    @Test
    @DisplayName("정상 신청 시 PENDING 상태 신청이 생성된다")
    void apply_success_creates_pending_application() {
        given(merchantApplicationRepository.existsByUserIdAndStatusIn(USER_ID, BLOCKING_STATUSES))
                .willReturn(false);
        given(merchantApplicationRepository.existsByBusinessNumberAndStatusIn(VALID_BIZ_NORMALIZED, BLOCKING_STATUSES))
                .willReturn(false);
        MerchantApplication saved = MerchantApplication.create(USER_ID, makeRequest("우리떡볶이", VALID_BIZ_NUMBER));
        ReflectionTestUtils.setField(saved, "id", 1L);
        given(merchantApplicationRepository.save(any(MerchantApplication.class))).willReturn(saved);

        MerchantApplicationResponse response = merchantApplicationService.apply(USER_ID, makeRequest("우리떡볶이", VALID_BIZ_NUMBER));

        verify(merchantApplicationRepository).save(any(MerchantApplication.class));
        assertThat(response.status()).isEqualTo(MerchantApplicationStatus.PENDING);
        assertThat(response.shopName()).isEqualTo("우리떡볶이");
    }

    @Test
    @DisplayName("PENDING 신청이 이미 있으면 MERCHANT_CERT_ALREADY_PENDING 예외")
    void apply_duplicatePending_throws_MERCHANT_001() {
        given(merchantApplicationRepository.existsByUserIdAndStatusIn(USER_ID, BLOCKING_STATUSES))
                .willReturn(true);

        assertThatThrownBy(() -> merchantApplicationService.apply(USER_ID, makeRequest("테스트", VALID_BIZ_NUMBER)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.MERCHANT_CERT_ALREADY_PENDING);

        verify(merchantApplicationRepository, never()).save(any());
    }

    @Test
    @DisplayName("APPROVED 신청이 이미 있으면 MERCHANT_CERT_ALREADY_PENDING 예외")
    void apply_alreadyApproved_throws_MERCHANT_001() {
        given(merchantApplicationRepository.existsByUserIdAndStatusIn(USER_ID, BLOCKING_STATUSES))
                .willReturn(true);

        assertThatThrownBy(() -> merchantApplicationService.apply(USER_ID, makeRequest("테스트", VALID_BIZ_NUMBER)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.MERCHANT_CERT_ALREADY_PENDING);

        verify(merchantApplicationRepository, never()).save(any());
    }

    @Test
    @DisplayName("잘못된 사업자등록번호 체크섬 시 INVALID_BUSINESS_NUMBER 예외")
    void apply_invalidBizNumber_throws_MERCHANT_002() {
        given(merchantApplicationRepository.existsByUserIdAndStatusIn(USER_ID, BLOCKING_STATUSES))
                .willReturn(false);

        // 101-81-34619: 마지막 자리 9 (올바른 체크섬은 8)
        assertThatThrownBy(() -> merchantApplicationService.apply(USER_ID, makeRequest("테스트", "101-81-34619")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_BUSINESS_NUMBER);
    }

    @Test
    @DisplayName("하이픈 없는 10자리 사업자번호도 정상 처리된다")
    void apply_bizNumberWithoutHyphen_success() {
        given(merchantApplicationRepository.existsByUserIdAndStatusIn(USER_ID, BLOCKING_STATUSES))
                .willReturn(false);
        given(merchantApplicationRepository.existsByBusinessNumberAndStatusIn(VALID_BIZ_NORMALIZED, BLOCKING_STATUSES))
                .willReturn(false);
        MerchantApplication saved = MerchantApplication.create(USER_ID, makeRequest("테스트가게", VALID_BIZ_NORMALIZED));
        ReflectionTestUtils.setField(saved, "id", 2L);
        given(merchantApplicationRepository.save(any(MerchantApplication.class))).willReturn(saved);

        MerchantApplicationResponse response = merchantApplicationService.apply(USER_ID, makeRequest("테스트가게", VALID_BIZ_NORMALIZED));

        assertThat(response.status()).isEqualTo(MerchantApplicationStatus.PENDING);
    }

    @Test
    @DisplayName("다른 유저가 동일 사업자번호로 PENDING/APPROVED 신청 중이면 DUPLICATE_BUSINESS_NUMBER 예외")
    void apply_duplicateBizNumberByOtherUser_throws_MERCHANT_004() {
        given(merchantApplicationRepository.existsByUserIdAndStatusIn(USER_ID, BLOCKING_STATUSES))
                .willReturn(false);
        given(merchantApplicationRepository.existsByBusinessNumberAndStatusIn(VALID_BIZ_NORMALIZED, BLOCKING_STATUSES))
                .willReturn(true);

        assertThatThrownBy(() -> merchantApplicationService.apply(USER_ID, makeRequest("테스트", VALID_BIZ_NUMBER)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_BUSINESS_NUMBER);

        verify(merchantApplicationRepository, never()).save(any());
    }

    @Test
    @DisplayName("동시 요청이 DB 유니크 제약(uk_merchant_active_business_number) 위반 시 DUPLICATE_BUSINESS_NUMBER 예외")
    void apply_concurrentRequest_dbConstraintViolation_throws_MERCHANT_004() {
        given(merchantApplicationRepository.existsByUserIdAndStatusIn(USER_ID, BLOCKING_STATUSES))
                .willReturn(false);
        given(merchantApplicationRepository.existsByBusinessNumberAndStatusIn(VALID_BIZ_NORMALIZED, BLOCKING_STATUSES))
                .willReturn(false);
        willThrow(new DataIntegrityViolationException("uk_merchant_active_business_number"))
                .given(merchantApplicationRepository).save(any(MerchantApplication.class));

        assertThatThrownBy(() -> merchantApplicationService.apply(USER_ID, makeRequest("테스트", VALID_BIZ_NUMBER)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_BUSINESS_NUMBER);
    }

    @Test
    @DisplayName("REJECTED된 사업자번호는 재사용 가능 — existsByBusinessNumberAndStatusIn이 false 반환 시 통과")
    void apply_rejectedBizNumberCanBeReused() {
        given(merchantApplicationRepository.existsByUserIdAndStatusIn(USER_ID, BLOCKING_STATUSES))
                .willReturn(false);
        // REJECTED 상태만 있는 경우 false 반환 (PENDING/APPROVED 없음)
        given(merchantApplicationRepository.existsByBusinessNumberAndStatusIn(VALID_BIZ_NORMALIZED, BLOCKING_STATUSES))
                .willReturn(false);
        MerchantApplication saved = MerchantApplication.create(USER_ID, makeRequest("재신청가게", VALID_BIZ_NUMBER));
        ReflectionTestUtils.setField(saved, "id", 3L);
        given(merchantApplicationRepository.save(any(MerchantApplication.class))).willReturn(saved);

        MerchantApplicationResponse response = merchantApplicationService.apply(USER_ID, makeRequest("재신청가게", VALID_BIZ_NUMBER));

        assertThat(response.status()).isEqualTo(MerchantApplicationStatus.PENDING);
    }
}
