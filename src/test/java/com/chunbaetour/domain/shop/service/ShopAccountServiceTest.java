package com.chunbaetour.domain.shop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.shop.dto.request.ShopAccountRequest;
import com.chunbaetour.domain.shop.dto.response.ShopAccountResponse;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.entity.ShopWallet;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.shop.repository.ShopWalletRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShopAccountServiceTest {

    @Mock
    private ShopRepository shopRepository;
    @Mock
    private ShopWalletRepository shopWalletRepository;

    @InjectMocks
    private ShopAccountService shopAccountService;

    private static final Long USER_ID = 1L;
    private static final Long SHOP_ID = 10L;

    private Shop createShop() {
        return Shop.builder()
                .userId(USER_ID).applicationId(1L).shopName("테스트 가게")
                .category("FOOD").address("서울").phone("02-1234-5678").description("").build();
    }

    private ShopWallet createWallet() {
        return ShopWallet.create(SHOP_ID);
    }

    @Test
    @DisplayName("정산 계좌 등록 — 정상 저장 후 마스킹된 응답 반환")
    void updateAccount_success() {
        ShopAccountRequest req = new ShopAccountRequest("국민은행", "123456-78-901234", "홍길동");
        given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.of(createShop()));
        given(shopWalletRepository.findByShopId(SHOP_ID)).willReturn(Optional.of(createWallet()));

        ShopAccountResponse result = shopAccountService.updateAccount(USER_ID, SHOP_ID, req);

        assertThat(result.shopId()).isEqualTo(SHOP_ID);
        assertThat(result.bankName()).isEqualTo("국민은행");
        assertThat(result.accountHolder()).isEqualTo("홍길동");
        // 뒷 4자리만 노출
        assertThat(result.accountNumber()).endsWith("1234");
        assertThat(result.accountNumber()).contains("*");
    }

    @Test
    @DisplayName("정산 계좌 등록 — 타인 가게 → SHOP_NOT_FOUND")
    void updateAccount_notOwner_throws() {
        ShopAccountRequest req = new ShopAccountRequest("국민은행", "123456-78-901234", "홍길동");
        given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> shopAccountService.updateAccount(USER_ID, SHOP_ID, req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_NOT_FOUND);
    }

    @Test
    @DisplayName("정산 계좌 등록 — 지갑 없음 → SHOP_WALLET_NOT_FOUND")
    void updateAccount_walletNotFound_throws() {
        ShopAccountRequest req = new ShopAccountRequest("국민은행", "123456-78-901234", "홍길동");
        given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.of(createShop()));
        given(shopWalletRepository.findByShopId(SHOP_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> shopAccountService.updateAccount(USER_ID, SHOP_ID, req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_WALLET_NOT_FOUND);
    }

    @Test
    @DisplayName("정산 계좌 등록 — SUSPENDED 가게 → SHOP_INACTIVE")
    void updateAccount_suspendedShop_throws() {
        ShopAccountRequest req = new ShopAccountRequest("국민은행", "123456-78-901234", "홍길동");
        Shop shop = createShop();
        shop.hide(); // ACTIVE → SUSPENDED
        given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.of(shop));

        assertThatThrownBy(() -> shopAccountService.updateAccount(USER_ID, SHOP_ID, req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_INACTIVE);
    }

    @Test
    @DisplayName("계좌번호 마스킹 — 뒷 4자리 제외 * 처리, 하이픈 유지")
    void accountNumberMasking_hidesMiddleDigits() {
        ShopAccountRequest req = new ShopAccountRequest("신한은행", "110-123-456789", "김철수");
        given(shopRepository.findByIdAndUserId(SHOP_ID, USER_ID)).willReturn(Optional.of(createShop()));
        given(shopWalletRepository.findByShopId(SHOP_ID)).willReturn(Optional.of(createWallet()));

        ShopAccountResponse result = shopAccountService.updateAccount(USER_ID, SHOP_ID, req);

        assertThat(result.accountNumber()).endsWith("6789");
        assertThat(result.accountNumber()).doesNotContain("110");
    }
}
