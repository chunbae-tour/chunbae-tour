package com.chunbaetour.domain.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.merchant.dto.request.MerchantApplyRequest;
import com.chunbaetour.domain.merchant.dto.response.MerchantApplicationResponse;
import com.chunbaetour.domain.merchant.entity.MerchantApplication;
import com.chunbaetour.domain.merchant.repository.MerchantApplicationRepository;
import com.chunbaetour.domain.merchant.type.MerchantApplicationStatus;
import java.math.BigDecimal;
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

    // 유효한 사업자등록번호 (체크섬 통과): 123-12-31231 (검증값 계산 완료)
    private static final String VALID_BIZ_NUMBER = "123-12-31231";
    private static final String VALID_BIZ_NORMALIZED = "1231231231";

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
        given(merchantApplicationRepository.existsByUserIdAndStatus(USER_ID, MerchantApplicationStatus.PENDING))
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
        given(merchantApplicationRepository.existsByUserIdAndStatus(USER_ID, MerchantApplicationStatus.PENDING))
                .willReturn(true);

        assertThatThrownBy(() -> merchantApplicationService.apply(USER_ID, makeRequest("테스트", VALID_BIZ_NUMBER)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.MERCHANT_CERT_ALREADY_PENDING);

        verifyNoMoreInteractions(merchantApplicationRepository);
    }

    @Test
    @DisplayName("잘못된 사업자등록번호 체크섬 시 INVALID_BUSINESS_NUMBER 예외")
    void apply_invalidBizNumber_throws_MERCHANT_002() {
        given(merchantApplicationRepository.existsByUserIdAndStatus(USER_ID, MerchantApplicationStatus.PENDING))
                .willReturn(false);

        // 체크섬 오류: 마지막 자리 변조
        assertThatThrownBy(() -> merchantApplicationService.apply(USER_ID, makeRequest("테스트", "101-81-34618")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_BUSINESS_NUMBER);
    }

    @Test
    @DisplayName("하이픈 없는 10자리 사업자번호도 정상 처리된다")
    void apply_bizNumberWithoutHyphen_success() {
        given(merchantApplicationRepository.existsByUserIdAndStatus(USER_ID, MerchantApplicationStatus.PENDING))
                .willReturn(false);
        MerchantApplication saved = MerchantApplication.create(USER_ID, makeRequest("테스트가게", VALID_BIZ_NORMALIZED));
        ReflectionTestUtils.setField(saved, "id", 2L);
        given(merchantApplicationRepository.save(any(MerchantApplication.class))).willReturn(saved);

        MerchantApplicationResponse response = merchantApplicationService.apply(USER_ID, makeRequest("테스트가게", VALID_BIZ_NORMALIZED));

        assertThat(response.status()).isEqualTo(MerchantApplicationStatus.PENDING);
    }

    @Test
    @DisplayName("사업자번호가 10자리가 아니면 INVALID_BUSINESS_NUMBER 예외")
    void apply_bizNumberWrongLength_throws_MERCHANT_002() {
        given(merchantApplicationRepository.existsByUserIdAndStatus(USER_ID, MerchantApplicationStatus.PENDING))
                .willReturn(false);

        // 9자리 숫자 (10자리 미만)
        assertThatThrownBy(() -> merchantApplicationService.apply(USER_ID, makeRequest("테스트", "123456789")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_BUSINESS_NUMBER);
    }
}
