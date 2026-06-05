package com.chunbaetour.domain.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;

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
import com.chunbaetour.domain.shop.entity.ShopWallet;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.shop.repository.ShopWalletRepository;
import com.chunbaetour.domain.common.util.CursorUtils;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
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

    @Mock
    private ShopWalletRepository shopWalletRepository;

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

    private Account merchantAccount() {
        Account account = (Account) ReflectionTestUtils.invokeMethod(
                Account.class, "createForSeed",
                "merchant@example.com", "hashed", "상인닉네임",
                com.chunbaetour.domain.auth.Role.MERCHANT,
                com.chunbaetour.domain.auth.AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(account, "id", USER_ID);
        return account;
    }

    /** shopRepository.save stub — 저장된 Shop에 id=100L 세팅 후 반환 */
    private void givenShopSavedWithId() {
        given(shopRepository.save(any(Shop.class))).willAnswer(inv -> {
            Shop s = inv.getArgument(0);
            ReflectionTestUtils.setField(s, "id", 100L);
            return s;
        });
    }

    // ===== approve =====

    @Test
    @DisplayName("승인 성공: application APPROVED, account MERCHANT, Shop 생성")
    void approve_success() {
        MerchantApplication app = pendingApplication();
        Account account = activeAccount();

        given(applicationRepository.findByIdWithLock(APPLICATION_ID)).willReturn(Optional.of(app));
        given(accountRepository.findByIdWithLock(USER_ID)).willReturn(Optional.of(account));
        givenShopSavedWithId();
        given(shopWalletRepository.save(any(ShopWallet.class))).willAnswer(inv -> inv.getArgument(0));

        MerchantApplicationDetailResponse response = adminMerchantApplicationService.approve(APPLICATION_ID, null);

        assertThat(response.status()).isEqualTo(MerchantApplicationStatus.APPROVED);
        assertThat(account.getRole()).isEqualTo(com.chunbaetour.domain.auth.Role.MERCHANT);
        verify(shopRepository).save(any(Shop.class));
        verify(shopWalletRepository).save(any(ShopWallet.class));
    }

    @Test
    @DisplayName("승인 실패: 존재하지 않는 신청 → MERCHANT_006")
    void approve_notFound_throws() {
        given(applicationRepository.findByIdWithLock(APPLICATION_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminMerchantApplicationService.approve(APPLICATION_ID, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.MERCHANT_APPLICATION_NOT_FOUND.getMessage());

        verify(accountRepository, never()).findByIdWithLock(any());
        verify(shopRepository, never()).save(any());
    }

    @Test
    @DisplayName("승인 성공: 이미 MERCHANT인 상인의 추가 가게 신청 — role 유지, Shop 생성")
    void approve_accountAlreadyMerchant_success() {
        MerchantApplication app = pendingApplication();
        Account merchantAccount = (Account) ReflectionTestUtils.invokeMethod(
                Account.class, "createForSeed",
                "merchant@example.com", "hashed", "상인닉",
                com.chunbaetour.domain.auth.Role.MERCHANT,
                com.chunbaetour.domain.auth.AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(merchantAccount, "id", USER_ID);

        given(applicationRepository.findByIdWithLock(APPLICATION_ID)).willReturn(Optional.of(app));
        given(accountRepository.findByIdWithLock(USER_ID)).willReturn(Optional.of(merchantAccount));
        givenShopSavedWithId();
        given(shopWalletRepository.save(any(ShopWallet.class))).willAnswer(inv -> inv.getArgument(0));

        MerchantApplicationDetailResponse response = adminMerchantApplicationService.approve(APPLICATION_ID, null);

        assertThat(response.status()).isEqualTo(MerchantApplicationStatus.APPROVED);
        assertThat(merchantAccount.getRole()).isEqualTo(com.chunbaetour.domain.auth.Role.MERCHANT);
        verify(shopRepository).save(any(Shop.class));
        verify(shopWalletRepository).save(any(ShopWallet.class));
    }

    @Test
    @DisplayName("승인 실패: 이미 APPROVED 상태 → MERCHANT_005")
    void approve_alreadyApproved_throws() {
        MerchantApplication app = pendingApplication();
        Account account = activeAccount();
        given(applicationRepository.findByIdWithLock(APPLICATION_ID)).willReturn(Optional.of(app));
        given(accountRepository.findByIdWithLock(USER_ID)).willReturn(Optional.of(account));
        givenShopSavedWithId();
        given(shopWalletRepository.save(any(ShopWallet.class))).willAnswer(inv -> inv.getArgument(0));
        adminMerchantApplicationService.approve(APPLICATION_ID, null); // 첫 번째 승인

        // 첫 번째 승인 후 application=APPROVED, account=MERCHANT — 두 번째 요청은 role 선제 검증에서 차단
        given(applicationRepository.findByIdWithLock(APPLICATION_ID)).willReturn(Optional.of(app));
        given(accountRepository.findByIdWithLock(USER_ID)).willReturn(Optional.of(account));

        assertThatThrownBy(() -> adminMerchantApplicationService.approve(APPLICATION_ID, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.MERCHANT_APPLICATION_STATUS_INVALID.getMessage());
    }

    @Test
    @DisplayName("승인 성공: 이미 MERCHANT인 계정의 새 신청서 승인 — 다중 가게 허용 (KAN-361)")
    void approve_merchantAccount_secondShop_success() {
        MerchantApplication app = pendingApplication();
        Account account = merchantAccount(); // 이미 MERCHANT 역할 — promoteToMerchant 멱등 처리
        given(applicationRepository.findByIdWithLock(APPLICATION_ID)).willReturn(Optional.of(app));
        given(accountRepository.findByIdWithLock(USER_ID)).willReturn(Optional.of(account));
        givenShopSavedWithId();
        given(shopWalletRepository.save(any(ShopWallet.class))).willAnswer(inv -> inv.getArgument(0));

        MerchantApplicationDetailResponse response =
                adminMerchantApplicationService.approve(APPLICATION_ID, null);

        assertThat(response).isNotNull();
        verify(shopRepository).save(any(Shop.class));
        verify(shopWalletRepository).save(any(ShopWallet.class));
    }

    @Test
    @DisplayName("승인 실패: ShopWallet uk_shop_wallets_shop_id 중복 → SHOP_WALLET_ALREADY_EXISTS")
    void approve_shopWalletDuplicate_throwsShopAlreadyExists() {
        MerchantApplication app = pendingApplication();
        Account account = activeAccount();
        given(applicationRepository.findByIdWithLock(APPLICATION_ID)).willReturn(Optional.of(app));
        given(accountRepository.findByIdWithLock(USER_ID)).willReturn(Optional.of(account));
        givenShopSavedWithId();

        RuntimeException cause = new RuntimeException("Duplicate entry for key 'uk_shop_wallets_shop_id'");
        given(shopWalletRepository.save(any(ShopWallet.class)))
                .willThrow(new DataIntegrityViolationException("constraint violation", cause));

        assertThatThrownBy(() -> adminMerchantApplicationService.approve(APPLICATION_ID, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_WALLET_ALREADY_EXISTS);
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
    @DisplayName("목록 조회: cursor 없음, PENDING, 다음 페이지 있음")
    void getApplications_noCursor_hasNext() {
        MerchantApplication app = pendingApplication();
        given(applicationRepository.findByStatusOrderByIdDesc(
                MerchantApplicationStatus.PENDING, PageRequest.of(0, 3)))
                .willReturn(List.of(app, app, app)); // size=2, size+1=3개 반환 → hasNext=true

        CursorPageResponse<MerchantApplicationDetailResponse> result =
                adminMerchantApplicationService.getApplications(null, 2, MerchantApplicationStatus.PENDING);

        assertThat(result.hasNext()).isTrue();
        assertThat(result.content()).hasSize(2);
        assertThat(result.nextCursor()).isNotNull();
    }

    @Test
    @DisplayName("목록 조회: cursor 있음, 두 번째 페이지, 다음 없음")
    void getApplications_withCursor_noNext() {
        MerchantApplication app = pendingApplication();
        String cursor = CursorUtils.encode(1L);

        given(applicationRepository.findByStatusAndIdLessThanOrderByIdDesc(
                MerchantApplicationStatus.PENDING, 1L, PageRequest.of(0, 3)))
                .willReturn(List.of(app));

        CursorPageResponse<MerchantApplicationDetailResponse> result =
                adminMerchantApplicationService.getApplications(cursor, 2, MerchantApplicationStatus.PENDING);

        assertThat(result.hasNext()).isFalse();
        assertThat(result.content()).hasSize(1);
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    @DisplayName("목록 조회: APPROVED status 필터 적용")
    void getApplications_approvedStatus() {
        MerchantApplication app = pendingApplication();
        // APPROVED 상태로 전이
        app.approve();
        given(applicationRepository.findByStatusOrderByIdDesc(
                MerchantApplicationStatus.APPROVED, PageRequest.of(0, 21)))
                .willReturn(List.of(app));

        CursorPageResponse<MerchantApplicationDetailResponse> result =
                adminMerchantApplicationService.getApplications(null, 20, MerchantApplicationStatus.APPROVED);

        assertThat(result.hasNext()).isFalse();
        assertThat(result.content()).hasSize(1);
    }

    @Test
    @DisplayName("목록 조회: 잘못된 cursor 형식 → INVALID_CURSOR")
    void getApplications_invalidCursor_throws() {
        assertThatThrownBy(() -> adminMerchantApplicationService.getApplications(
                "not-valid-base64!!!", 2, MerchantApplicationStatus.PENDING))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.INVALID_CURSOR.getMessage());
    }

    @Test
    @DisplayName("목록 조회: size=0 → INVALID_INPUT_VALUE")
    void getApplications_sizeZero_throws() {
        assertThatThrownBy(() -> adminMerchantApplicationService.getApplications(
                null, 0, MerchantApplicationStatus.PENDING))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("목록 조회: size > 100 → INVALID_INPUT_VALUE")
    void getApplications_sizeOverLimit_throws() {
        assertThatThrownBy(() -> adminMerchantApplicationService.getApplications(
                null, 101, MerchantApplicationStatus.PENDING))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    // ===== getApplication 단건 상세 조회 (KAN-182, Epic KAN-177 S13) =====

    @Test
    @DisplayName("단건 조회 성공: applicationId로 DTO 매핑 + 모든 필드 노출")
    void getApplication_success_returnsDto() {
        MerchantApplication app = pendingApplication();
        given(applicationRepository.findById(APPLICATION_ID)).willReturn(Optional.of(app));

        MerchantApplicationDetailResponse response = adminMerchantApplicationService.getApplication(APPLICATION_ID);

        assertThat(response.applicationId()).isEqualTo(APPLICATION_ID);
        assertThat(response.userId()).isEqualTo(USER_ID);
        assertThat(response.shopName()).isEqualTo("테스트가게");
        assertThat(response.businessNumber()).isEqualTo("1234567890");
        assertThat(response.category()).isEqualTo("한식");
        assertThat(response.address()).isEqualTo("서울시 강남구");
        assertThat(response.status()).isEqualTo(MerchantApplicationStatus.PENDING);
        assertThat(response.rejectReason()).isNull();
        // approve/reject 흐름이 PESSIMISTIC_WRITE를 쓰는 것과 달리, 본 GET은 단순 readOnly 조회 — 락 호출 없음
        verify(applicationRepository, never()).findByIdWithLock(any());
    }

    @Test
    @DisplayName("단건 조회 실패: 미존재 ID → MERCHANT_APPLICATION_NOT_FOUND")
    void getApplication_notFound_throws() {
        given(applicationRepository.findById(APPLICATION_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminMerchantApplicationService.getApplication(APPLICATION_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.MERCHANT_APPLICATION_NOT_FOUND.getMessage());
    }
}
