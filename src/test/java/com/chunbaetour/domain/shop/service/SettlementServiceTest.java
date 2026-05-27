package com.chunbaetour.domain.shop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.chunbaetour.domain.common.error.BusinessException;
import com.chunbaetour.domain.common.error.ErrorCode;
import com.chunbaetour.domain.common.response.CursorPageResponse;
import com.chunbaetour.domain.shop.dto.response.SettlementResponse;
import com.chunbaetour.domain.shop.entity.Settlement;
import com.chunbaetour.domain.shop.entity.Shop;
import com.chunbaetour.domain.shop.entity.ShopWallet;
import com.chunbaetour.domain.shop.repository.SettlementRepository;
import com.chunbaetour.domain.shop.repository.ShopRepository;
import com.chunbaetour.domain.shop.repository.ShopWalletRepository;
import com.chunbaetour.domain.shop.type.SettlementStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SettlementServiceTest {

    @Mock private SettlementRepository settlementRepository;
    @Mock private ShopRepository shopRepository;
    @Mock private ShopWalletRepository shopWalletRepository;

    @InjectMocks
    private SettlementService settlementService;

    private static final Long USER_ID = 1L;
    private static final Long SHOP_ID = 10L;
    private static final long BALANCE = 50_000L;

    private Shop createShop() {
        Shop shop = mock(Shop.class);
        given(shop.getId()).willReturn(SHOP_ID);
        return shop;
    }

    private ShopWallet createWallet(long balance) {
        ShopWallet wallet = mock(ShopWallet.class);
        given(wallet.getBalance()).willReturn(balance);
        given(wallet.getBankName()).willReturn("국민은행");
        given(wallet.getAccountNumber()).willReturn("123-456-789");
        given(wallet.getAccountHolder()).willReturn("홍길동");
        return wallet;
    }

    private Settlement createSettlement(Long id) {
        Settlement s = Settlement.create(SHOP_ID, BALANCE, "국민은행", "123-456-789", "홍길동");
        org.springframework.test.util.ReflectionTestUtils.setField(s, "id", id);
        return s;
    }

    @Test
    @DisplayName("정산 신청 성공 — Settlement 저장 및 응답 반환")
    void requestSettlement_success() {
        // given
        Shop shop = createShop();
        ShopWallet wallet = createWallet(BALANCE);
        Settlement settlement = createSettlement(1L);

        given(shopRepository.findByUserId(USER_ID)).willReturn(Optional.of(shop));
        given(settlementRepository.existsByShopIdAndStatus(SHOP_ID, SettlementStatus.PENDING)).willReturn(false);
        given(shopWalletRepository.findByShopIdWithLock(SHOP_ID)).willReturn(Optional.of(wallet));
        given(settlementRepository.save(any(Settlement.class))).willReturn(settlement);

        // when
        SettlementResponse response = settlementService.requestSettlement(USER_ID);

        // then
        assertThat(response.settlementId()).isEqualTo(1L);
        assertThat(response.amount()).isEqualTo(BALANCE);
        then(settlementRepository).should().save(any(Settlement.class));
    }

    @Test
    @DisplayName("가게 없음 — SHOP_NOT_FOUND")
    void requestSettlement_shopNotFound() {
        given(shopRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> settlementService.requestSettlement(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_NOT_FOUND);

        then(settlementRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("중복 PENDING 신청 — DUPLICATE_SETTLEMENT_REQUEST")
    void requestSettlement_duplicatePending() {
        Shop shop = createShop();
        ShopWallet wallet = createWallet(BALANCE);
        given(shopRepository.findByUserId(USER_ID)).willReturn(Optional.of(shop));
        given(shopWalletRepository.findByShopIdWithLock(SHOP_ID)).willReturn(Optional.of(wallet));
        given(settlementRepository.existsByShopIdAndStatus(SHOP_ID, SettlementStatus.PENDING)).willReturn(true);

        assertThatThrownBy(() -> settlementService.requestSettlement(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_SETTLEMENT_REQUEST);

        then(settlementRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("지갑 없음 — SHOP_WALLET_NOT_FOUND")
    void requestSettlement_walletNotFound() {
        Shop shop = createShop();
        given(shopRepository.findByUserId(USER_ID)).willReturn(Optional.of(shop));
        given(shopWalletRepository.findByShopIdWithLock(SHOP_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> settlementService.requestSettlement(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SHOP_WALLET_NOT_FOUND);
    }

    @Test
    @DisplayName("잔액 0 신청 — SETTLEMENT_BALANCE_EMPTY")
    void requestSettlement_zeroBalance() {
        Shop shop = createShop();
        ShopWallet wallet = createWallet(0L);

        given(shopRepository.findByUserId(USER_ID)).willReturn(Optional.of(shop));
        given(settlementRepository.existsByShopIdAndStatus(SHOP_ID, SettlementStatus.PENDING)).willReturn(false);
        given(shopWalletRepository.findByShopIdWithLock(SHOP_ID)).willReturn(Optional.of(wallet));

        assertThatThrownBy(() -> settlementService.requestSettlement(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SETTLEMENT_BALANCE_EMPTY);

        then(settlementRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("최소 금액 미달 신청 — SETTLEMENT_AMOUNT_TOO_LOW")
    void requestSettlement_belowMinimum() {
        Shop shop = createShop();
        ShopWallet wallet = createWallet(4_999L); // 5,000 미만

        given(shopRepository.findByUserId(USER_ID)).willReturn(Optional.of(shop));
        given(settlementRepository.existsByShopIdAndStatus(SHOP_ID, SettlementStatus.PENDING)).willReturn(false);
        given(shopWalletRepository.findByShopIdWithLock(SHOP_ID)).willReturn(Optional.of(wallet));

        assertThatThrownBy(() -> settlementService.requestSettlement(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SETTLEMENT_AMOUNT_TOO_LOW);

        then(settlementRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("내 정산 내역 조회 — 첫 페이지 (hasNext=false)")
    void getMySettlements_firstPage() {
        Shop shop = createShop();
        Settlement s1 = createSettlement(2L);
        Settlement s2 = createSettlement(1L);

        given(shopRepository.findByUserId(USER_ID)).willReturn(Optional.of(shop));
        given(settlementRepository.findByShopId(eq(SHOP_ID), isNull(), any(Pageable.class)))
                .willReturn(List.of(s1, s2));

        CursorPageResponse<SettlementResponse> result =
                settlementService.getMySettlements(USER_ID, null, 20);

        assertThat(result.content()).hasSize(2);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    @DisplayName("내 정산 내역 조회 — 다음 페이지 존재 (hasNext=true)")
    void getMySettlements_hasNext() {
        Shop shop = createShop();
        Settlement s1 = createSettlement(3L);
        Settlement s2 = createSettlement(2L);
        Settlement s3 = createSettlement(1L);

        given(shopRepository.findByUserId(USER_ID)).willReturn(Optional.of(shop));
        given(settlementRepository.findByShopId(eq(SHOP_ID), isNull(), any(Pageable.class)))
                .willReturn(List.of(s1, s2, s3));

        CursorPageResponse<SettlementResponse> result =
                settlementService.getMySettlements(USER_ID, null, 2);

        assertThat(result.content()).hasSize(2);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isNotNull();
    }
}
