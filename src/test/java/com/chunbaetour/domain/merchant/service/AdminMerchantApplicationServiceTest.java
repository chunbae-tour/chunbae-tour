package com.chunbaetour.domain.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunbaetour.domain.auth.Account;
import com.chunbaetour.domain.auth.AccountRepository;
import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.merchant.dto.response.MerchantApplicationDetailResponse;
import com.chunbaetour.domain.merchant.entity.MerchantApplication;
import com.chunbaetour.domain.merchant.repository.MerchantApplicationRepository;
import com.chunbaetour.domain.merchant.type.MerchantApplicationStatus;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminMerchantApplicationServiceTest {

    private static final Long APPLICATION_ID = 1L;
    private static final Long USER_ID = 10L;

    @InjectMocks
    private AdminMerchantApplicationService adminMerchantApplicationService;

    @Mock
    private MerchantApplicationRepository applicationRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private ShopRepository shopRepository;

    private MerchantApplication pendingApplication() {
        MerchantApplication app = MerchantApplication.create(USER_ID,
                new com.chunbaetour.domain.merchant.dto.request.MerchantApplyRequest(
                        "테스트가게", "1234567890", "한식", "서울시 강남구",
                        new BigDecimal("37.5665"), new BigDecimal("126.9780"), null, null));
        ReflectionTestUtils.setField(app, "id", APPLICATION_ID);
        return app;
    }

    private Account activeAccount() {
        Account account = (Account) ReflectionTestUtils.invokeMethod(
                Account.class, "createForSeed",
                "user@example.com", "hashed", "닉네임",
                com.chunbaetour.domain.auth.Role.USER,
                com.chunbaetour.domain.auth.AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(account, "id", USER_ID);
        return account;
    }

    // ===== approve =====

    @Test
    @DisplayName("승인 성공: application APPROVED, account MERCHANT, Shop 생성")
    void approve_success() {
        MerchantApplication app = pendingApplication();
        Account account = activeAccount();

        given(applicationRepository.findByIdWithLock(APPLICATION_ID)).willReturn(Optional.of(app));
        given(accountRepository.findByIdWithLock(USER_ID)).willReturn(Optional.of(account));
        given(shopRepository.existsByUserId(USER_ID)).willReturn(false);
        given(shopRepository.save(any(Shop.class))).willAnswer(inv -> inv.getArgument(0));

        MerchantApplicationDetailResponse response = adminMerchantApplicationService.approve(APPLICATION_ID);

        assertThat(response.status()).isEqualTo(MerchantApplicationStatus.APPROVED);
        assertThat(account.getRole()).isEqualTo(com.chunbaetour.domain.auth.Role.MERCHANT);
        verify(shopRepository).save(any(Shop.class));
    }

    @Test
    @DisplayName("승인 실패: 존재하지 않는 신청 → MERCHANT_006")
    void approve_notFound_throws() {
        given(applicationRepository.findByIdWithLock(APPLICATION_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminMerchantApplicationService.approve(APPLICATION_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.MERCHANT_APPLICATION_NOT_FOUND.getMessage());

        verify(accountRepository, never()).findByIdWithLock(any());
        verify(shopRepository, never()).save(any());
    }

    @Test
    @DisplayName("승인 실패: 이미 가게 있음 → SHOP_ALREADY_EXISTS")
    void approve_shopAlreadyExists_throws() {
        MerchantApplication app = pendingApplication();
        Account account = activeAccount();

        given(applicationRepository.findByIdWithLock(APPLICATION_ID)).willReturn(Optional.of(app));
        given(accountRepository.findByIdWithLock(USER_ID)).willReturn(Optional.of(account));
        given(shopRepository.existsByUserId(USER_ID)).willReturn(true);

        assertThatThrownBy(() -> adminMerchantApplicationService.approve(APPLICATION_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.SHOP_ALREADY_EXISTS.getMessage());

        verify(shopRepository, never()).save(any());
    }

    @Test
    @DisplayName("승인 실패: 이미 APPROVED 상태 → MERCHANT_005")
    void approve_alreadyApproved_throws() {
        MerchantApplication app = pendingApplication();
        Account account = activeAccount();
        given(applicationRepository.findByIdWithLock(APPLICATION_ID)).willReturn(Optional.of(app));
        given(accountRepository.findByIdWithLock(USER_ID)).willReturn(Optional.of(account));
        given(shopRepository.existsByUserId(USER_ID)).willReturn(false);
        given(shopRepository.save(any(Shop.class))).willAnswer(inv -> inv.getArgument(0));
        adminMerchantApplicationService.approve(APPLICATION_ID); // 첫 번째 승인

        // 이미 APPROVED인 상태에서 다시 findByIdWithLock 반환
        given(applicationRepository.findByIdWithLock(APPLICATION_ID)).willReturn(Optional.of(app));

        assertThatThrownBy(() -> adminMerchantApplicationService.approve(APPLICATION_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.MERCHANT_APPLICATION_STATUS_INVALID.getMessage());
    }

    // ===== reject =====

    @Test
    @DisplayName("거절 성공: 거절 사유 있음")
    void reject_withReason_success() {
        MerchantApplication app = pendingApplication();
        given(applicationRepository.findByIdWithLock(APPLICATION_ID)).willReturn(Optional.of(app));

        MerchantApplicationDetailResponse response =
                adminMerchantApplicationService.reject(APPLICATION_ID, "서류 미비");

        assertThat(response.status()).isEqualTo(MerchantApplicationStatus.REJECTED);
        assertThat(response.rejectReason()).isEqualTo("서류 미비");
    }

    @Test
    @DisplayName("거절 성공: 거절 사유 없음(null)")
    void reject_nullReason_success() {
        MerchantApplication app = pendingApplication();
        given(applicationRepository.findByIdWithLock(APPLICATION_ID)).willReturn(Optional.of(app));

        MerchantApplicationDetailResponse response =
                adminMerchantApplicationService.reject(APPLICATION_ID, null);

        assertThat(response.status()).isEqualTo(MerchantApplicationStatus.REJECTED);
        assertThat(response.rejectReason()).isNull();
    }

    @Test
    @DisplayName("거절 실패: 존재하지 않는 신청 → MERCHANT_006")
    void reject_notFound_throws() {
        given(applicationRepository.findByIdWithLock(APPLICATION_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminMerchantApplicationService.reject(APPLICATION_ID, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.MERCHANT_APPLICATION_NOT_FOUND.getMessage());
    }

    // ===== getApplications =====

    @Test
    @DisplayName("목록 조회: size 범위 초과 → INVALID_PAGE_SIZE")
    void getApplications_invalidSize_throws() {
        assertThatThrownBy(() -> adminMerchantApplicationService.getApplications(null, 101))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.INVALID_PAGE_SIZE.getMessage());
    }

    @Test
    @DisplayName("목록 조회: cursor 없음, 다음 페이지 있음")
    void getApplications_noCursor_hasNext() {
        MerchantApplication app = pendingApplication();
        given(applicationRepository.findByStatusOrderByIdDesc(
                MerchantApplicationStatus.PENDING, PageRequest.of(0, 3)))
                .willReturn(List.of(app, app, app)); // size=2, size+1=3개 반환 → hasNext=true

        CursorPageResponse<MerchantApplicationDetailResponse> result =
                adminMerchantApplicationService.getApplications(null, 2);

        assertThat(result.hasNext()).isTrue();
        assertThat(result.content()).hasSize(2);
        assertThat(result.nextCursor()).isNotNull();
    }
}
